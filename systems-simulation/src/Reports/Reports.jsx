import { useState, useEffect } from "react";
import { collection, onSnapshot, updateDoc, doc } from "firebase/firestore";
import { db } from "../firebase.js";

function Reports() {
    const [reports, setReports] = useState([]);
    const [status, setStatus] = useState("Loading reports...");

    useEffect(() => {
        const unsubscribe = onSnapshot(
            collection(db, "UserReports"),
            (snapshot) => {
                const sortedReports = snapshot.docs
                    .map((doc) => ({ id: doc.id, ...doc.data() }))
                    .sort((a, b) => (b.CreatedAt?.seconds || 0) - (a.CreatedAt?.seconds || 0));
                setReports(sortedReports);
                setStatus(`Loaded ${snapshot.size} reports.`);
            },
            (error) => {
                console.error("UserReports listener error:", error);
                setStatus(`Error loading reports: ${error.message}`);
            },
        );

        return () => unsubscribe();
    }, []);

    const resolveReport = async (reportId) => {
        try {
            await updateDoc(doc(db, "UserReports", reportId), { Status: "Solved" });
        } catch (error) {
            console.error("Failed to resolve report:", error);
        }
    };

    return (
        <>
            <p style={{ fontSize: "13px", color: "var(--text-muted, #888)", marginBottom: "16px" }}>{status}</p>
            {reports.length > 0 ? (
                <div style={{ overflowX: "auto" }}>
                    <table
                        style={{
                            width: "100%",
                            borderCollapse: "collapse",
                            background: "var(--bg-alt, #1a1a1a)",
                            borderRadius: "8px",
                            overflow: "hidden",
                        }}
                    >
                        <thead>
                            <tr style={{ background: "var(--accent-bg, #333)" }}>
                                <th
                                    style={{
                                        padding: "12px",
                                        textAlign: "left",
                                        color: "var(--text-h, #fff)",
                                        borderBottom: "1px solid var(--border)",
                                    }}
                                >
                                    Amenity Name
                                </th>
                                <th
                                    style={{
                                        padding: "12px",
                                        textAlign: "left",
                                        color: "var(--text-h, #fff)",
                                        borderBottom: "1px solid var(--border)",
                                    }}
                                >
                                    Description
                                </th>
                                <th
                                    style={{
                                        padding: "12px",
                                        textAlign: "left",
                                        color: "var(--text-h, #fff)",
                                        borderBottom: "1px solid var(--border)",
                                    }}
                                >
                                    Report Type
                                </th>
                                <th
                                    style={{
                                        padding: "12px",
                                        textAlign: "left",
                                        color: "var(--text-h, #fff)",
                                        borderBottom: "1px solid var(--border)",
                                    }}
                                >
                                    Created At
                                </th>
                                <th
                                    style={{
                                        padding: "12px",
                                        textAlign: "left",
                                        color: "var(--text-h, #fff)",
                                        borderBottom: "1px solid var(--border)",
                                    }}
                                >
                                    Status
                                </th>
                                <th
                                    style={{
                                        padding: "12px",
                                        textAlign: "center",
                                        color: "var(--text-h, #fff)",
                                        borderBottom: "1px solid var(--border)",
                                    }}
                                >
                                    Action
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {reports.map((report) => (
                                <tr key={report.id} style={{ borderBottom: "1px solid var(--border)" }}>
                                    <td style={{ padding: "12px", color: "var(--text-h, #fff)" }}>
                                        {report.AmenityName || "N/A"}
                                    </td>
                                    <td style={{ padding: "12px", color: "var(--text-h, #fff)" }}>
                                        {report.Description || "N/A"}
                                    </td>
                                    <td style={{ padding: "12px", color: "var(--text-h, #fff)" }}>
                                        {report.ReportType || "N/A"}
                                    </td>
                                    <td style={{ padding: "12px", color: "var(--text-h, #fff)" }}>
                                        {report.CreatedAt
                                            ? new Date(report.CreatedAt.seconds * 1000).toLocaleString()
                                            : "N/A"}
                                    </td>
                                    <td style={{ padding: "12px", color: "var(--text-h, #fff)" }}>
                                        {report.Status || "Active"}
                                    </td>
                                    <td style={{ padding: "12px", textAlign: "center" }}>
                                        {report.Status !== "Solved" && (
                                            <button
                                                onClick={() => resolveReport(report.id)}
                                                style={{
                                                    padding: "8px 16px",
                                                    backgroundColor: "#10b981",
                                                    color: "white",
                                                    border: "none",
                                                    borderRadius: "4px",
                                                    cursor: "pointer",
                                                }}
                                            >
                                                Resolve
                                            </button>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            ) : (
                <p>No reports found yet.</p>
            )}
        </>
    );
}

export default Reports;
