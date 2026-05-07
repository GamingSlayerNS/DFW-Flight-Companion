import { useState, useEffect } from "react";
import { ref, onValue } from "firebase/database";
import { rtdb } from "../firebase";
import HallwayCard from "./HallwayCard";

function Hallway() {
    const [nodes, setNodes] = useState({});
    const [status, setStatus] = useState("Connecting…");

    useEffect(() => {
        const graphRef = ref(rtdb, "MapData/CurrentGraph/edges");

        const unsubscribe = onValue(
            graphRef,
            (snapshot) => {
                const data = snapshot.val() ?? {};
                setNodes(data);
                setStatus(`Loaded ${Object.keys(data).length} edges.`);
            },
            (error) => {
                console.error("RTDB listener error:", error);
                setStatus(`Error: ${error.message}`);
            }
        );

        return () => unsubscribe();
    }, []);

    const nodeEntries = Object.entries(nodes);

    console.log
    return (
        <div>
            <p style={{ fontSize: "13px", color: "var(--text-muted, #888)", marginBottom: "16px" }}>
                {status}
            </p>
            {nodeEntries.length > 0 ? (
                <div className="amenity-grid">
                    {nodeEntries.map(([nodeId, data]) => (
                        <HallwayCard key={nodeId} nodeId={nodeId} data={data} />
                    ))}
                </div>
            ) : (
                <p style={{ color: "var(--text-muted, #888)", fontSize: "14px" }}>
                    No edges found under <code>MapData/currentGraph/edges</code>.
                </p>
            )}
        </div>
    );
}

export default Hallway;
