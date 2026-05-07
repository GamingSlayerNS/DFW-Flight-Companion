import { useState, useEffect } from "react";
import { collection, getDocs, doc, updateDoc } from "firebase/firestore";
import reactLogo from "./assets/react.svg";
import viteLogo from "./assets/vite.svg";
import heroImg from "./assets/hero.png";
import "./App.css";
import { db } from "./firebase.js";

function App() {
    const [count, setCount] = useState(0);
    const [status, setStatus] = useState("Connecting to Firestore...");
    const [docs, setDocs] = useState([]);
    const [selectedAmenityId, setSelectedAmenityId] = useState("");
    const [toggleLoading, setToggleLoading] = useState(false);

    useEffect(() => {
        async function loadFirestore() {
            try {
                const snapshot = await getDocs(collection(db, "Amenity"));
                setDocs(snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() })));
                setStatus(`Connected to ${db.app.options.projectId}. Loaded ${snapshot.size} docs.`);
            } catch (error) {
                console.error("Firestore connection failed:", error);
                setStatus(`Firestore connection failed: ${error.message}`);
            }
        }

        loadFirestore();
    }, []);

    const handleToggleAccessible = async () => {
        if (!selectedAmenityId) return;

        setToggleLoading(true);
        try {
            const selectedDoc = docs.find((d) => d.id === selectedAmenityId);
            const currentValue = selectedDoc?.IsAccessible ?? false;

            await updateDoc(doc(db, "Amenity", selectedAmenityId), {
                IsAccessible: !currentValue,
            });

            // Update local state
            setDocs(docs.map((d) => (d.id === selectedAmenityId ? { ...d, IsAccessible: !d.IsAccessible } : d)));
        } catch (error) {
            console.error("Error toggling IsAccessible:", error);
            alert(`Error: ${error.message}`);
        } finally {
            setToggleLoading(false);
        }
    };

    return (
        <>
            <section id="center">
                <div className="hero">
                    <img src={heroImg} className="base" width="170" height="179" alt="" />
                    <img src={reactLogo} className="framework" alt="React logo" />
                    <img src={viteLogo} className="vite" alt="Vite logo" />
                </div>
                <div>
                    <h1>DFW Flight Companion Simulation</h1>
                    <p>{status}</p>
                    <button type="button" className="counter" onClick={() => setCount((count) => count + 1)}>
                        Count is {count}
                    </button>
                </div>
            </section>

            <div className="ticks"></div>

            <section id="next-steps" style={{ display: "flex", flexDirection: "column" }}>
                <div id="simulation-controls">
                    <h2>Amenity Controls</h2>
                    <div style={{ marginBottom: "1rem" }}>
                        <label htmlFor="amenity-select" style={{ marginRight: "0.5rem" }}>
                            Select Amenity:
                        </label>
                        <select
                            id="amenity-select"
                            value={selectedAmenityId}
                            onChange={(e) => setSelectedAmenityId(e.target.value)}
                        >
                            <option value="">-- Choose an amenity --</option>
                            {docs.map((doc) => (
                                <option key={doc.id} value={doc.id}>
                                    {doc.id}
                                </option>
                            ))}
                        </select>
                    </div>
                    {selectedAmenityId && (
                        <>
                            <div style={{ marginBottom: "1rem" }}>
                                <strong>Current IsAccessible: </strong>
                                {docs.find((d) => d.id === selectedAmenityId)?.IsAccessible ? "✓ True" : "✗ False"}
                            </div>
                            <button
                                onClick={handleToggleAccessible}
                                disabled={toggleLoading}
                                style={{
                                    padding: "0.5rem 1rem",
                                    cursor: toggleLoading ? "not-allowed" : "pointer",
                                    opacity: toggleLoading ? 0.6 : 1,
                                }}
                            >
                                {toggleLoading ? "Updating..." : "Toggle IsAccessible"}
                            </button>
                        </>
                    )}
                </div>
                <div id="docs">
                    <h2>Firestore sample documents</h2>
                    {docs.length > 0 ? (
                        <ul style={{ display: "flex", flexDirection: "column" }}>
                            {docs.map((doc) => (
                                <li style={{}} key={doc.id}>
                                    {doc.id}: {JSON.stringify(doc)}
                                </li>
                            ))}
                        </ul>
                    ) : (
                        <p>
                            No documents found in the <code>simulation</code> collection yet.
                        </p>
                    )}
                </div>
            </section>

            <div className="ticks"></div>
            <section id="spacer"></section>
        </>
    );
}

export default App;

