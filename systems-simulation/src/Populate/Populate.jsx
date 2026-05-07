import routingRaw from "/src/assets/mapdata/routing.geojson?raw";
import floorplanRaw from "/src/assets/mapdata/floorplan.geojson?raw";
import { collection, getDocs, writeBatch, doc, GeoPoint } from "firebase/firestore";
import { httpsCallable } from "firebase/functions";
import { db, functions } from "../firebase";
import { useState } from "react";

const GEO_COLLECTIONS = ["MapBackground", "MapNode", "PathEdge"];
const MISC_COLLECTIONS = ["Terminal", "AmenityUnit", "AmenitySchedule", "Sensor", "User", "UserReports"];

async function wipeGeoCollections(addLog) {
    for (const name of GEO_COLLECTIONS) {
        const snapshot = await getDocs(collection(db, name));
        const batch = writeBatch(db);
        snapshot.forEach((d) => batch.delete(d.ref));
        await batch.commit();
        addLog(`Wiped ${name} (${snapshot.size} docs)`);
    }
}

async function wipeMisc(addLog) {
    for (const name of MISC_COLLECTIONS) {
        const snapshot = await getDocs(collection(db, name));
        const batch = writeBatch(db);
        snapshot.forEach((d) => batch.delete(d.ref));
        await batch.commit();
        addLog(`Wiped ${name} (${snapshot.size} docs)`);
    }
}

async function populateBackground(addLog) {
    const floorplanData = JSON.parse(floorplanRaw);
    const batch = writeBatch(db);
    let count = 0;

    floorplanData.features.forEach((feature) => {
        const props = feature.properties;
        const geom = feature.geometry;
        if (geom.type !== "Polygon") return;

        const points = geom.coordinates[0].map(([lng, lat]) => new GeoPoint(lat, lng));

        batch.set(doc(db, "MapBackground", props.id), {
            id: props.id,
            type: props.type,
            name: props.name,
            level: props.level,
            gender: props.gender ?? "",
            coordinates: points,
        });
        count++;
    });

    await batch.commit();
    addLog(`MapBackgrounds seeded: ${count} polygons.`);
}

async function populateNodes(addLog) {
    const routingData = JSON.parse(routingRaw);
    const batch = writeBatch(db);
    let count = 0;

    routingData.features.forEach((feature) => {
        const props = feature.properties;
        const geom = feature.geometry;
        if (props.type !== "poi") return;

        const [lng, lat] = geom.coordinates;

        batch.set(doc(db, "MapNode", props.id), {
            id: props.id,
            terminalId: "Terminal D",
            type: "poi",
            name: props.name,
            level: props.level,
            gender: props.gender ?? "",
            coordinates: new GeoPoint(lat, lng),
        });
        count++;
    });

    await batch.commit();
    addLog(`MapNodes seeded: ${count} nodes.`);
}

async function populatePathEdges(addLog) {
    const routingData = JSON.parse(routingRaw);
    const batch = writeBatch(db);
    let segmentCount = 0;

    routingData.features.forEach((feature) => {
        const props = feature.properties;
        const geom = feature.geometry;
        if (props.type !== "path") return;

        const coords = geom.coordinates;

        for (let i = 0; i < coords.length - 1; i++) {
            const [lngA, latA] = coords[i];
            const [lngB, latB] = coords[i + 1];
            const segmentId = coords.length > 2 ? `${props.id}_${i + 1}` : props.id;
            const segmentName = coords.length > 2 ? `${props.name}_${i + 1}` : props.id;
            batch.set(doc(db, "PathEdge", segmentId), {
                id: segmentId,
                type: "path",
                name: segmentName,
                coordinates: [new GeoPoint(latA, lngA), new GeoPoint(latB, lngB)],
                isOpen: true,
            });
            segmentCount++;
        }
    });

    await batch.commit();
    addLog(`PathEdges seeded: ${segmentCount} segments.`);
}

async function populateGeoCollection(addLog) {
    await wipeGeoCollections(addLog);
    await populateNodes(addLog);
    await populateBackground(addLog);
    await populatePathEdges(addLog);
}

async function publishNavigationGraph(addLog) {
    try {
        const fn = httpsCallable(functions, "publishGraphToRealtime");
        const result = await fn();
        addLog("publishNavigationGraph result: " + JSON.stringify(result.data));
    } catch (e) {
        console.error("publishNavigationGraph failed:", e);
    }
}

async function populateAmenity(addLog) {
    try {
        const amenitySnapshot = await getDocs(collection(db, "Amenity"));
        const deleteBatch = writeBatch(db);
        amenitySnapshot.forEach((doc) => deleteBatch.delete(doc.ref));
        await deleteBatch.commit();
        addLog("Wiped old amenities. Adding new ones...");

        const routingData = JSON.parse(routingRaw);
        const routingFeatures = routingData.features;

        const amenityBatch = writeBatch(db);
        let amenityCount = 0;

        for (const feature of routingFeatures) {
            const props = feature.properties;
            if (props.type !== "poi") continue;

            const id = props.id;
            const amenityRef = doc(collection(db, "Amenity"), id);

            amenityBatch.set(amenityRef, {
                AmenityID: id,
                Name: props.name,
                AmenityType: "Restroom",
                SubTypeName: props.gender ?? "",
                Congestion: "Low",
                WaitTime: 0.0,
                LastUpdated: Date.now(),
                IsAccessible: true,
                NodeID: id,
            });
            amenityCount++;
        }
        //add console logging for updates

        await amenityBatch.commit();
        addLog(`Successfully added ${amenityCount} amenities.`);
    } catch (e) {
        console.error("Error during amenity population", e);
    }
}

async function populateAmenityUnits(addLog) {
    try {
        await Promise.all([wipeCollection("AmenityUnit"), wipeCollection("AmenitySchedule"), wipeCollection("Sensor")]);

        const amenitySnapshot = await getDocs(collection(db, "Amenity"));
        const amenityDocs = amenitySnapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }));

        let unitBatch = writeBatch(db);
        let detailCount = 0;
        let amenityCount = 0;

        const commitUnitBatchIfFull = async () => {
            if (detailCount >= 400) {
                await unitBatch.commit();
                unitBatch = writeBatch(db);
                detailCount = 0;
            }
        };

        for (const amenity of amenityDocs) {
            const amenityId = amenity.AmenityID ?? amenity.id;
            const accessible = String(amenity.SubTypeName ?? "")
                .toLowerCase()
                .includes("accessible");
            const unitCount = accessible ? 2 : 6;

            unitBatch.set(doc(collection(db, "AmenitySchedule"), `${amenityId}_schedule`), {
                AmenityScheduleID: `${amenityId}_schedule`,
                AmenityID: amenityId,
                OperatingHours: "9am-5pm",
                OpenTime: "09:00",
                CloseTime: "17:00",
                IsOpen: true,
            });
            detailCount++;

            for (let unitIndex = 1; unitIndex <= unitCount; unitIndex++) {
                const unitId = `${amenityId}_unit${unitIndex}`;
                const sensorId = `${amenityId}_sensor${unitIndex}`;

                unitBatch.set(doc(collection(db, "AmenityUnit"), unitId), {
                    AmenityUnitID: unitId,
                    AmenityID: amenityId,
                    SensorID: sensorId,
                    UnitStatus: "Open",
                    LastUpdated: Date.now(),
                    IsOccupied: false,
                });
                detailCount++;

                unitBatch.set(doc(collection(db, "Sensor"), sensorId), {
                    SensorID: sensorId,
                    AmenityUnitID: unitId,
                    AmenityID: amenityId,
                    SensorType: "Occupancy",
                    IsOccupied: false,
                    Status: "Idle",
                    LastUpdate: Date.now(),
                });
                detailCount++;
            }

            amenityCount++;
            await commitUnitBatchIfFull();
        }

        if (detailCount > 0) await unitBatch.commit();
        addLog(`Successfully added unit/sensor/schedule data for ${amenityCount} amenities.`);
    } catch (e) {
        console.error("Error during amenity unit population", e);
    }
}

async function wipeCollection(name) {
    const snapshot = await getDocs(collection(db, name));
    const batch = writeBatch(db);
    snapshot.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
}

async function populateMisc(addLog) {
    await wipeMisc(addLog);

    const batch = writeBatch(db);

    batch.set(doc(collection(db, "Terminal")), {
        Name: "Terminal D",
        Description: "DFW International Terminal",
        Center: new GeoPoint(32.8974, -97.0446),
    });

    batch.set(doc(collection(db, "User")), {
        UserID: "U1",
        Email: "example@email.com",
        Username: "testUser",
        CreatedAt: Date.now(),
    });

    batch.set(doc(collection(db, "UserReports")), {
        ReportID: "R1",
        UserID: "U1",
        NodeID: "N1",
        Description: "Broken restroom",
        ReportType: "Maintenance",
    });

    await batch.commit();
    addLog("Misc collections seeded.");
}

function Populate() {
    const [logs, setLogs] = useState([]);

    const addLog = (message) => {
        setLogs((prevLogs) => [...prevLogs, `${new Date().toLocaleTimeString()}: ${message}`]);
    };

    const buttonStyle = {
        padding: "12px 24px",
        fontSize: "16px",
        fontWeight: "600",
        border: "none",
        borderRadius: "8px",
        cursor: "pointer",
        transition: "all 0.3s ease",
        boxShadow: "0 2px 8px rgba(0, 0, 0, 0.1)",
        color: "white",
        minWidth: "200px",
    };

    const geoButtonStyle = {
        ...buttonStyle,
        backgroundColor: "#3b82f6",
        "&:hover": { backgroundColor: "#2563eb" },
    };

    const miscButtonStyle = {
        ...buttonStyle,
        backgroundColor: "#8b5cf6",
        "&:hover": { backgroundColor: "#7c3aed" },
    };

    const amenityButtonStyle = {
        ...buttonStyle,
        backgroundColor: "#ec4899",
        "&:hover": { backgroundColor: "#db2777" },
    };

    const unitsButtonStyle = {
        ...buttonStyle,
        backgroundColor: "#f59e0b",
        "&:hover": { backgroundColor: "#d97706" },
    };

    const graphButtonStyle = {
        ...buttonStyle,
        backgroundColor: "#10b981",
        "&:hover": { backgroundColor: "#059669" },
    };

    return (
        <div style={{ display: "flex", gap: "20px", padding: "20px" }}>
            <div style={{ display: "flex", flexDirection: "column", gap: "12px", flex: 1 }}>
                <h2 style={{ marginTop: 0, color: "#333" }}>Database Population</h2>
                <button
                    onClick={async () => {
                        setLogs([]);
                        await populateGeoCollection(addLog);
                    }}
                    style={geoButtonStyle}
                    onMouseEnter={(e) => (e.target.style.backgroundColor = "#2563eb")}
                    onMouseLeave={(e) => (e.target.style.backgroundColor = "#3b82f6")}
                    onMouseDown={(e) => (e.target.style.transform = "scale(0.98)")}
                    onMouseUp={(e) => (e.target.style.transform = "scale(1)")}
                >
                    Populate Geo
                </button>
                <button
                    onClick={async () => {
                        setLogs([]);
                        await populateMisc(addLog);
                    }}
                    style={miscButtonStyle}
                    onMouseEnter={(e) => (e.target.style.backgroundColor = "#7c3aed")}
                    onMouseLeave={(e) => (e.target.style.backgroundColor = "#8b5cf6")}
                    onMouseDown={(e) => (e.target.style.transform = "scale(0.98)")}
                    onMouseUp={(e) => (e.target.style.transform = "scale(1)")}
                >
                    Populate Misc
                </button>
                <button
                    onClick={async () => {
                        setLogs([]);
                        await populateAmenity(addLog);
                    }}
                    style={amenityButtonStyle}
                    onMouseEnter={(e) => (e.target.style.backgroundColor = "#db2777")}
                    onMouseLeave={(e) => (e.target.style.backgroundColor = "#ec4899")}
                    onMouseDown={(e) => (e.target.style.transform = "scale(0.98)")}
                    onMouseUp={(e) => (e.target.style.transform = "scale(1)")}
                >
                    Populate Amenity
                </button>
                <button
                    onClick={async () => {
                        setLogs([]);
                        await populateAmenityUnits(addLog);
                    }}
                    style={unitsButtonStyle}
                    onMouseEnter={(e) => (e.target.style.backgroundColor = "#d97706")}
                    onMouseLeave={(e) => (e.target.style.backgroundColor = "#f59e0b")}
                    onMouseDown={(e) => (e.target.style.transform = "scale(0.98)")}
                    onMouseUp={(e) => (e.target.style.transform = "scale(1)")}
                >
                    Populate Amenity Units
                </button>
                <button
                    onClick={async () => {
                        setLogs([]);
                        await publishNavigationGraph(addLog);
                    }}
                    style={graphButtonStyle}
                    onMouseEnter={(e) => (e.target.style.backgroundColor = "#059669")}
                    onMouseLeave={(e) => (e.target.style.backgroundColor = "#10b981")}
                    onMouseDown={(e) => (e.target.style.transform = "scale(0.98)")}
                    onMouseUp={(e) => (e.target.style.transform = "scale(1)")}
                >
                    Publish Navigation Graph
                </button>
            </div>
            <div
                style={{
                    flex: 1,
                    border: "1px solid #ccc",
                    padding: "10px",
                    borderRadius: "8px",
                    backgroundColor: "#f9f9f9",
                }}
            >
                <h3>Logs</h3>
                <div style={{ maxHeight: "400px", overflowY: "auto", fontFamily: "monospace", fontSize: "14px" }}>
                    {logs.length === 0 ? (
                        <p>No logs yet.</p>
                    ) : (
                        logs.map((log, index) => (
                            <div key={index} style={{ marginBottom: "5px" }}>
                                {log}
                            </div>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
}

export default Populate;
