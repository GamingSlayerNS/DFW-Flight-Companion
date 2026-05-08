import { useState, useEffect } from "react";
import { collection, onSnapshot, doc, updateDoc } from "firebase/firestore";
import { ref, onValue } from "firebase/database";
import reactLogo from "./assets/react.svg";
import viteLogo from "./assets/vite.svg";
import heroImg from "./assets/hero.png";
import "./App.css";
import { db, rtdb } from "./firebase.js";
import Populate from "./Populate/Populate.jsx";
import Amenity from "./Amenity/Amenity.jsx";
import AmenityUnit from "./AmenityUnits/AmenityUnit.jsx";
import Hallway from "./Hallway/Hallway.jsx";

function App() {
    const [status, setStatus] = useState("Connecting to Firestore...");
    const [activeTab, setActiveTab] = useState("Amenity");

    const [amenities, setAmenities] = useState([]);
    const [amenityStatus, setAmenityStatus] = useState("");
    useEffect(() => {
        setStatus("Connecting to Firestore...");

        const unsubscribe = onSnapshot(
            collection(db, "Amenity"),
            (snapshot) => {
                setAmenities(snapshot.docs.map((d) => ({ id: d.id, ...d.data() })));
                setStatus(`Connected to ${db.app.options.projectId}.`);
                setAmenityStatus(`Loaded ${snapshot.size} amenities.`);
            },
            (error) => {
                console.error("Firestore listener error:", error);
                setStatus(`Firestore error: ${error.message}`);
            },
        );

        return () => unsubscribe();
    }, []);

    const [nodes, setNodes] = useState({});
    const [nodeStatus, setNodeStatus] = useState("");
    useEffect(() => {
        const graphRef = ref(rtdb, "MapData/CurrentGraph/edges");

        const unsubscribe = onValue(
            graphRef,
            (snapshot) => {
                const data = snapshot.val() ?? {};
                setNodes(data);
                setNodeStatus(`Loaded ${Object.keys(data).length} edges.`);
            },
            (error) => {
                console.error("RTDB listener error:", error);
                setStatus(`Error: ${error.message}`);
            },
        );

        return () => unsubscribe();
    }, []);

    return (
        <>
            <section id="center">
                <div>
                    <h1>DFW Flight Companion Simulator</h1>
                    <p>{status}</p>
                </div>
            </section>
            <div className="ticks"></div>

            <section id="next-steps" style={{ display: "flex", flexDirection: "column" }}>
                <nav className="tab-bar">
                    {["Amenity", "Stalls", "Hallway", "Populate"].map((tab) => (
                        <button
                            key={tab}
                            className={`tab-button${activeTab === tab ? " tab-button--active" : ""}`}
                            onClick={() => setActiveTab(tab)}
                        >
                            {tab}
                        </button>
                    ))}
                </nav>

                <div className="tab-content">
                    {activeTab === "Amenity" && <Amenity amenities={amenities} status={amenityStatus} />}
                    {activeTab === "Stalls" && <AmenityUnit amenities={amenities} status={amenityStatus} />}

                    {activeTab === "Hallway" && <Hallway nodes={nodes} status={nodeStatus} />}

                    {activeTab === "Populate" && <Populate />}
                </div>
            </section>

            <div className="ticks"></div>
            <section id="spacer"></section>
        </>
    );
}

export default App;
