import { useState, useEffect } from "react";
import { collection, onSnapshot, doc, updateDoc } from "firebase/firestore";
import reactLogo from "./assets/react.svg";
import viteLogo from "./assets/vite.svg";
import heroImg from "./assets/hero.png";
import "./App.css";
import { db } from "./firebase.js";
import Populate from "./Populate/Populate.jsx";
import AmenityCard from "./AmenityCard/AmenityCard.jsx";
import Hallway from "./Hallway/Hallway.jsx";

function App() {
    const [count, setCount] = useState(0);
    const [status, setStatus] = useState("Connecting to Firestore...");
    const [docs, setDocs] = useState([]);
    const [selectedAmenityId, setSelectedAmenityId] = useState("");
    const [search, setSearch] = useState("");
    const [activeTab, setActiveTab] = useState("Amenity");
    const [toggleLoading, setToggleLoading] = useState(false);

    useEffect(() => {
        setStatus("Connecting to Firestore...");

        const unsubscribe = onSnapshot(
            collection(db, "Amenity"),
            (snapshot) => {
                setDocs(snapshot.docs.map((d) => ({ id: d.id, ...d.data() })));
                setStatus(`Connected to ${db.app.options.projectId}. Loaded ${snapshot.size} docs.`);
            },
            (error) => {
                console.error("Firestore listener error:", error);
                setStatus(`Firestore error: ${error.message}`);
            }
        );

        return () => unsubscribe();
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
                <div>
                    <h1>DFW Flight Companion Simulation</h1>
                    <p>{status}</p>
                </div>
            </section>
            <div className="ticks"></div>

            <section id="next-steps" style={{ display: "flex", flexDirection: "column" }}>
                <nav className="tab-bar">
                    {["Amenity", "Hallway", "Populate"].map((tab) => (
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
                    {activeTab === "Amenity" && (
                        <>
                            <input
                                className="amenity-search"
                                type="search"
                                placeholder="Search by name or ID…"
                                value={search}
                                onChange={(e) => setSearch(e.target.value)}
                            />
                            {docs.length > 0 ? (
                                <div className="amenity-grid">
                                    {docs
                                        .filter((doc) => {
                                            const q = search.toLowerCase();
                                            return (
                                                doc.Name?.toLowerCase().includes(q) ||
                                                doc.id?.toLowerCase().includes(q)
                                            );
                                        })
                                        .map((doc) => (
                                            <AmenityCard key={doc.id} amenity={doc} />
                                        ))}
                                </div>
                            ) : (
                                <p>No documents found in the <code>Amenity</code> collection yet.</p>
                            )}
                        </>
                    )}

                    {activeTab === "Hallway" && <Hallway />}

                    {activeTab === "Populate" && <Populate />}
                </div>
            </section>

            <div className="ticks"></div>
            <section id="spacer"></section>
        </>
    );
}

export default App;

