import { useState, useEffect, useMemo } from "react";
import { collection, onSnapshot, updateDoc, doc, query, where, getDocs } from "firebase/firestore";
import { db } from "../firebase.js";
import AmenityCard from "../Amenity/AmenityCard";

function AmenityUnit({ amenities = [], status }) {
    const [selectedAmenityId, setSelectedAmenityId] = useState("");
    const [amenityUnits, setAmenityUnits] = useState([]);
    const [sensors, setSensors] = useState([]);
    const [unitStatus, setUnitStatus] = useState("Connecting to stall data...");
    const [sensorStatus, setSensorStatus] = useState("");

    useEffect(() => {
        const unsubscribeUnits = onSnapshot(
            collection(db, "AmenityUnit"),
            (snapshot) => {
                setAmenityUnits(snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() })));
                setUnitStatus(`Loaded ${snapshot.size} stall units.`);
            },
            (error) => {
                console.error("AmenityUnit listener error:", error);
                setUnitStatus(`Error loading stall units: ${error.message}`);
            },
        );

        const unsubscribeSensors = onSnapshot(
            collection(db, "Sensor"),
            (snapshot) => {
                setSensors(snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() })));
                setSensorStatus(`Loaded ${snapshot.size} sensors.`);
            },
            (error) => {
                console.error("Sensor listener error:", error);
                setSensorStatus(`Error loading sensors: ${error.message}`);
            },
        );

        return () => {
            unsubscribeUnits();
            unsubscribeSensors();
        };
    }, []);

    const amenityOptions = amenities.slice().sort((a, b) => (a.Name || a.id || "").localeCompare(b.Name || b.id || ""));
    const selectedAmenity = amenityOptions.find((amenity) => amenity.id === selectedAmenityId) ?? null;

    const stalls = selectedAmenity
        ? amenityUnits.filter(
              (unit) => unit.AmenityID === selectedAmenityId || unit.AmenityID === selectedAmenity?.AmenityID,
          )
        : [];

    const sensorMap = useMemo(
        () => Object.fromEntries(sensors.map((sensor) => [sensor.SensorID ?? sensor.id, sensor])),
        [sensors],
    );

    const updateSensorStatus = async (sensorId, newStatus, relatedUnit) => {
        try {
            await updateDoc(doc(db, "Sensor", sensorId), { Status: newStatus });

            const unitUpdate = {
                IsOccupied: newStatus === "Active",
                UnitStatus: newStatus === "Broken" ? "Closed" : "Open",
            };

            if (relatedUnit) {
                await updateDoc(doc(db, "AmenityUnit", relatedUnit.id), unitUpdate);

                // Update Amenity Congestion based on occupancy
                const amenityId = relatedUnit.AmenityID;
                const unitsQuery = query(collection(db, "AmenityUnit"), where("AmenityID", "==", amenityId));
                const unitsSnapshot = await getDocs(unitsQuery);
                const totalUnits = unitsSnapshot.size;
                const occupiedUnits = unitsSnapshot.docs.filter((doc) => doc.data().IsOccupied).length;

                let congestion = "Low";
                const occupancyRatio = occupiedUnits / totalUnits;
                if (occupancyRatio > 0.66) congestion = "High";
                else if (occupancyRatio > 0.33) congestion = "Medium";

                await updateDoc(doc(db, "Amenity", amenityId), { Congestion: congestion });
            }
        } catch (error) {
            console.error("Failed to update sensor status:", error);
        }
    };

    return (
        <>
            <p style={{ fontSize: "13px", color: "var(--text-muted, #888)", marginBottom: "16px" }}>
                {status} {unitStatus} {sensorStatus}
            </p>

            <div style={{ display: "flex", flexDirection: "row", gap: "16px" }}>
                <div
                    style={{
                        width: "33.333%",
                        display: "flex",
                        flexDirection: "column",
                        gap: "16px",
                        marginBottom: "24px",
                    }}
                >
                    <div style={{ display: "flex", flexDirection: "column", gap: "20px", alignItems: "flex-start" }}>
                        <div style={{ display: "flex", flexDirection: "column", gap: "8px", flex: 1 }}>
                            <label htmlFor="amenity-select" style={{ fontWeight: 600, color: "var(--text-h, #fff)" }}>
                                Select Amenity
                            </label>
                            <select
                                id="amenity-select"
                                value={selectedAmenityId}
                                onChange={(event) => setSelectedAmenityId(event.target.value)}
                                style={{
                                    width: "100%",
                                    padding: "10px 12px",
                                    borderRadius: "8px",
                                    border: "1px solid var(--border)",
                                    background: "var(--bg-alt, #1a1a1a)",
                                    color: "var(--text-h, #fff)",
                                    fontSize: "14px",
                                }}
                            >
                                <option value="">Choose an Amenity</option>
                                {amenityOptions.map((amenity) => (
                                    <option key={amenity.id} value={amenity.id}>
                                        {amenity.Name || amenity.id}
                                    </option>
                                ))}
                            </select>
                        </div>
                        {selectedAmenity && (
                            <div style={{ flex: 1 }}>
                                <AmenityCard amenity={selectedAmenity} />
                            </div>
                        )}
                    </div>
                </div>

                {/* //GRID// */}
                <div style={{ width: "100%" }}>
                    {selectedAmenity ? (
                        <div style={{ display: "flex", flexWrap: "wrap", gap: "12px", alignItems: "flex-end" }}>
                            <div style={{ minWidth: "240px" }}>
                                <p style={{ margin: 0, fontSize: "14px", color: "var(--text-muted, #888)" }}>
                                    Showing {stalls.length} stall{stalls.length === 1 ? "" : "s"} for{" "}
                                    <strong>{selectedAmenity.Name || selectedAmenity.id}</strong>
                                </p>
                            </div>
                        </div>
                    ) : (
                        <p style={{ margin: 0, color: "var(--text-muted, #888)", fontSize: "14px" }}>
                            Pick an amenity to display its stalls and sensor details.
                        </p>
                    )}

                    {selectedAmenity ? (
                        stalls.length > 0 ? (
                            <div
                                style={{
                                    display: "grid",
                                    gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))",
                                    gap: "16px",
                                    marginTop: "8px",
                                }}
                            >
                                {stalls.map((stall) => {
                                    const sensor = sensorMap[stall.SensorID];
                                    return (
                                        <article
                                            key={stall.id}
                                            style={{
                                                border: "1px solid var(--border)",
                                                borderRadius: "16px",
                                                padding: "18px",
                                                background: "var(--bg-alt, #121212)",
                                                minHeight: "180px",
                                            }}
                                        >
                                            <h3 style={{ marginTop: 0, marginBottom: "10px", fontSize: "18px" }}>
                                                {stall.AmenityUnitID || stall.id}
                                            </h3>
                                            <p
                                                style={{
                                                    margin: "4px 0",
                                                    fontSize: "14px",
                                                }}
                                            >
                                                <strong>Unit status:</strong>{" "}
                                                <span
                                                    style={{
                                                        color:
                                                            stall.UnitStatus === "Open"
                                                                ? "#10b981"
                                                                : stall.UnitStatus === "Closed"
                                                                  ? "#ef4444"
                                                                  : "var(--text-h, #fff)",
                                                    }}
                                                >
                                                    {stall.UnitStatus ?? "Unknown"}
                                                </span>
                                            </p>
                                            <p style={{ margin: "4px 0", fontSize: "14px" }}>
                                                <strong>Occupied:</strong> {String(stall.IsOccupied ?? false)}
                                            </p>
                                            <p style={{ margin: "4px 0", fontSize: "14px" }}>
                                                <strong>Sensor:</strong> {stall.SensorID || "None"}
                                            </p>
                                            {sensor ? (
                                                <div
                                                    style={{
                                                        marginTop: "12px",
                                                        padding: "12px",
                                                        borderRadius: "12px",
                                                        background: "rgba(100, 111, 255, 0.08)",
                                                    }}
                                                >
                                                    <p
                                                        style={{
                                                            margin: "0 0 6px",
                                                            fontSize: "13px",
                                                            color: "var(--text-muted, #888)",
                                                        }}
                                                    >
                                                        Sensor details
                                                    </p>
                                                    <p style={{ margin: "4px 0", fontSize: "13px" }}>
                                                        <strong>Type:</strong> {sensor.SensorType || "Unknown"}
                                                    </p>
                                                    <p style={{ margin: "4px 0", fontSize: "13px" }}>
                                                        <strong>Status:</strong>{" "}
                                                        <select
                                                            value={sensor.Status || "Idle"}
                                                            onChange={(event) =>
                                                                updateSensorStatus(sensor.id, event.target.value, stall)
                                                            }
                                                            style={{
                                                                color: "var(--text-h, #fff)",
                                                                background: "var(--bg-alt, #1a1a1a)",
                                                                border: "1px solid var(--border)",
                                                                borderRadius: "8px",
                                                                padding: "4px 8px",
                                                                fontSize: "13px",
                                                            }}
                                                        >
                                                            <option value="Idle">Idle</option>
                                                            <option value="Active">Active</option>
                                                            <option value="Broken">Broken</option>
                                                        </select>
                                                    </p>
                                                </div>
                                            ) : (
                                                <p
                                                    style={{
                                                        marginTop: "12px",
                                                        fontSize: "13px",
                                                        color: "var(--text-muted, #888)",
                                                    }}
                                                >
                                                    No sensor document found for this stall.
                                                </p>
                                            )}
                                        </article>
                                    );
                                })}
                            </div>
                        ) : (
                            <p style={{ color: "var(--text-muted, #888)", fontSize: "14px" }}>
                                No stalls found for this amenity yet.
                            </p>
                        )
                    ) : null}
                </div>
            </div>
        </>
    );
}

export default AmenityUnit;
