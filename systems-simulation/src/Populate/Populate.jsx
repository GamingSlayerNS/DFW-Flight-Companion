import routingRaw from "/src/assets/mapdata/routing.geojson?raw";
import floorplanRaw from "/src/assets/mapdata/floorplan.geojson?raw";
import { collection, getDocs, writeBatch, doc } from "firebase/firestore";
import { httpsCallable } from "firebase/functions";
import { db, functions } from "../firebase";
import { GeoPoint } from "firebase/firestore";


function Populate(){
    const populateRestrooms = async () => {
    try {
        const amenitySnapshot = await getDocs(collection(db, "Amenity"));
        const deleteBatch = writeBatch(db);
        amenitySnapshot.forEach((doc) => deleteBatch.delete(doc.ref));
        await deleteBatch.commit();
        console.log("Wiped old amenities. Adding new ones...");

        const routingData = JSON.parse(routingRaw);
        const routingFeatures = routingData.features;
        const congestionLevels = ["Low", "Medium", "High"];

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
        console.log("Successfully added restrooms.");
    } catch (e) {
        console.error("Error during restroom population", e);
    }
    };


    const wipeAndSeed = async () => {
        const collections = [
            "Terminal", "MapNode", "PathEdge", "AmenityUnit", "AmenitySchedule", "Sensor", "User", "UserReports", "MapBackground"
        ];

        try {
            // 1. Wipe all collections
            console.log("Wiping all collections...");
            for (const collectionName of collections) {
            const snapshot = await getDocs(collection(db, collectionName));
            const batch = writeBatch(db);
            snapshot.forEach((d) => batch.delete(d.ref));
            await batch.commit();
            console.log(`Wiped ${collectionName} (${snapshot.size} docs)`);
            }

            // 2. Seed from GeoJSON
            await seedFromGeoJson();

        } catch (e) {
            console.error("Wipe and seed failed", e);
        }
    };

    const seedFromGeoJson = async () => {
    try {
        console.log("Starting GeoJSON Seed...");

        // 1. Seed Terminal Root
        const terminalRef = doc(collection(db, "Terminal"));
        const terminalBatch = writeBatch(db);
        terminalBatch.set(terminalRef, {
            Name: "Terminal D",
            Description: "DFW International Terminal",
            Center: { latitude: 32.8974, longitude: -97.0446 },
        });
        await terminalBatch.commit();

        // 2. Parse routing.geojson for MapNodes and PathEdges
        const routingData = JSON.parse(routingRaw);
        const routingFeatures = routingData.features;
        const routingBatch = writeBatch(db);

        routingFeatures.forEach((feature) => {
        const props = feature.properties;
        const geom = feature.geometry;
        const type = props.type;
        const id = props.id;

        if (type === "poi") {
            const [lng, lat] = geom.coordinates;
            const nodeRef = doc(db, "MapNode", id);
            routingBatch.set(nodeRef, {
                id,
                terminalId: "Terminal D",
                type: "poi",
                name: props.name,
                level: props.level,
                gender: props.gender,
                coordinates: { latitude: lat, longitude: lng },
            });
        } else if (type === "path") {
            const pathPoints = geom.coordinates.map(([lng, lat]) => ({
                latitude: lat,
                longitude: lng,
            }));
            const pathRef = doc(db, "PathEdge", id);
                routingBatch.set(pathRef, {
                id,
                type: "path",
                name: props.name,
                coordinates: pathPoints,
                isOpen: true,
            });
        }
        });

        await routingBatch.commit();
        console.log("MapNodes and PathEdges seeded.");

        // 3. Parse floorplan.geojson for MapBackgrounds
        const floorplanData = JSON.parse(floorplanRaw);
        const floorplanFeatures = floorplanData.features;
        const floorplanBatch = writeBatch(db);

        floorplanFeatures.forEach((feature) => {
        const props = feature.properties;
        const geom = feature.geometry;
        const id = props.id;

        if (geom.type === "Polygon") {
            const exteriorRing = geom.coordinates[0];
            const points = exteriorRing.map(([lng, lat]) => ({
                latitude: lat,
                longitude: lng,
            }));
            const bgRef = doc(db, "MapBackground", id);
            floorplanBatch.set(bgRef, {
                id,
                type: props.type,
                name: props.name,
                level: props.level,
                gender: props.gender,
                coordinates: points,
            });
        }
        });

        await floorplanBatch.commit();
        console.log("MapBackgrounds seeded.");

        // 4. Seed remaining collections
        const miscBatch = writeBatch(db);

        miscBatch.set(doc(collection(db, "AmenityUnit")), {
            StatusID: "S1",
            AmenityID: "A1",
            SensorID: "SN1",
            UnitStatus: "Open",
            LastUpdated: Date.now(),
        });

        miscBatch.set(doc(collection(db, "AmenitySchedule")), {
            AmenityScheduleID: "AS1",
            AmenityID: "A1",
            OperatingHours: "24/7",
        });

        miscBatch.set(doc(collection(db, "Sensor")), {
            SensorID: "SN1",
            SensorType: "Occupancy",
            Status: "Active",
            LastUpdate: Date.now(),
        });

        miscBatch.set(doc(collection(db, "User")), {
            UserID: "U1",
            Email: "example@email.com",
            Username: "testUser",
            CreatedAt: Date.now(),
        });

        miscBatch.set(doc(collection(db, "UserReports")), {
            ReportID: "R1",
            UserID: "U1",
            NodeID: "N1",
            Description: "Broken restroom",
            ReportType: "Maintenance",
        });

        await miscBatch.commit();
        console.log("GeoJSON Seeding Complete!");

    } catch (e) {
        console.error("GeoJSON Seed Failed", e);
    }
    };

    const publishNavigationGraph = async () => {
        try {
            const fn = httpsCallable(functions, "publishNavigationGraph");
            const result = await fn();
            console.log("publishNavigationGraph result:", result.data);
        } catch (e) {
            console.error("publishNavigationGraph failed:", e);
        }
    };

    return (
        <div style={{ display: "flex", gap: "8px" }}>
            <button onClick={populateRestrooms}>Populate Restrooms</button>
            <button onClick={wipeAndSeed}>Wipe and Seed</button>
            <button onClick={publishNavigationGraph}>Publish Navigation Graph</button>
        </div>
    );
}


export default Populate;
