import routingRaw from "/src/assets/mapdata/routing.geojson?raw";
import floorplanRaw from "/src/assets/mapdata/floorplan.geojson?raw";
import { collection, getDocs, writeBatch, doc, GeoPoint } from "firebase/firestore";
import { httpsCallable } from "firebase/functions";
import { db, functions } from "../firebase";

const GEO_COLLECTIONS = ["MapBackground", "MapNode", "PathEdge"];
const MISC_COLLECTIONS = ["Terminal", "AmenityUnit", "AmenitySchedule", "Sensor", "User", "UserReports"];

async function wipeGeoCollections() {
    for (const name of GEO_COLLECTIONS) {
        const snapshot = await getDocs(collection(db, name));
        const batch = writeBatch(db);
        snapshot.forEach((d) => batch.delete(d.ref));
        await batch.commit();
        console.log(`Wiped ${name} (${snapshot.size} docs)`);
    }
}

async function wipeMisc() {
    for (const name of MISC_COLLECTIONS) {
        const snapshot = await getDocs(collection(db, name));
        const batch = writeBatch(db);
        snapshot.forEach((d) => batch.delete(d.ref));
        await batch.commit();
        console.log(`Wiped ${name} (${snapshot.size} docs)`);
    }
}

async function populateBackground() {
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
            gender: props.gender ?? '',
            coordinates: points,
        });
        count++;
    });

    await batch.commit();
    console.log(`MapBackgrounds seeded: ${count} polygons.`);
}

async function populateNodes() {
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
            gender: props.gender ?? '',
            coordinates: [new GeoPoint(lat, lng)],
        });
        count++;
    });

    await batch.commit();
    console.log(`MapNodes seeded: ${count} nodes.`);
}

async function populatePathEdges() {
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
            const segmentId = coords.length > 2 ? `${props.id}_${i+1}` : props.id;
            const segmentName = coords.length > 2 ? `${props.name}_${i+1}` : props.id;
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
    console.log(`PathEdges seeded: ${segmentCount} segments.`);
}

async function populateGeoCollection() {
    await wipeGeoCollections();
    await populateNodes();
    await populateBackground();
    await populatePathEdges();
}

async function publishNavigationGraph() {
    try {
        const fn = httpsCallable(functions, "publishGraphToRealtime");
        const result = await fn();
        console.log("publishNavigationGraph result:", result.data);
    } catch (e) {
        console.error("publishNavigationGraph failed:", e);
    }
}

async function populateAmenity(){
    try {
        const amenitySnapshot = await getDocs(collection(db, "Amenity"));
        const deleteBatch = writeBatch(db);
        amenitySnapshot.forEach((doc) => deleteBatch.delete(doc.ref));
        await deleteBatch.commit();
        console.log("Wiped old amenities. Adding new ones...");

        const routingData = JSON.parse(routingRaw);
        const routingFeatures = routingData.features;

        const addBatch = writeBatch(db);
        routingFeatures.forEach((feature) => {
        const props = feature.properties;
        if (props.type === "poi") {
            const id = props.id;
            const docRef = doc(collection(db, "Amenity"), id);
            addBatch.set(docRef, {
                AmenityID: id,
                Name: props.name,
                AmenityType: "Restroom",
                SubTypeName: props.gender,
                Congestion: "Low",
                WaitTime: 0.0,
                LastUpdated: Date.now(),
                IsAccessible: true,
                NodeID: id,
            });
        }
        });

        await addBatch.commit();
        console.log("Successfully added amenities.");
    } catch (e) {
        console.error("Error during amenity population", e);
    }
}

async function populateMisc() {
    await wipeMisc();

    const batch = writeBatch(db);

    batch.set(doc(collection(db, "Terminal")), {
        Name: "Terminal D",
        Description: "DFW International Terminal",
        Center: new GeoPoint(32.8974, -97.0446),
    });

    batch.set(doc(collection(db, "AmenityUnit")), {
        StatusID: "S1",
        AmenityID: "A1",
        SensorID: "SN1",
        UnitStatus: "Open",
        LastUpdated: Date.now(),
    });

    batch.set(doc(collection(db, "AmenitySchedule")), {
        AmenityScheduleID: "AS1",
        AmenityID: "A1",
        OperatingHours: "24/7",
    });

    batch.set(doc(collection(db, "Sensor")), {
        SensorID: "SN1",
        SensorType: "Occupancy",
        Status: "Active",
        LastUpdate: Date.now(),
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
    console.log("Misc collections seeded.");
}

function Populate(){

    return (
        <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
            <div style={{ display: "flex", gap: "8px" }}>
                <button onClick={populateGeoCollection}>Populate Geo</button>
            </div>
            <div style={{ display: "flex", gap: "8px" }}>
                <button onClick={populateMisc}>Populate Misc</button>
            </div>
            <div style={{ display: "flex", gap: "8px" }}>
                <button onClick={populateAmenity}>Populate Amenity</button>
            </div>
            <div style={{ display: "flex", gap: "8px" }}>
                <button onClick={publishNavigationGraph}>Publish Navigation Graph</button>
            </div>
        </div>
    );
}


export default Populate;
