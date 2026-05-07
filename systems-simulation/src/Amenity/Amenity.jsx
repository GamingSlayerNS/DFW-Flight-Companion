import { useState } from "react";
import AmenityCard from "./AmenityCard";

function Amenity({ amenities, status }) {
    const [search, setSearch] = useState("");

    const filtered = amenities.filter((doc) => {
        const q = search.toLowerCase();
        return (
            doc.Name?.toLowerCase().includes(q) ||
            doc.id?.toLowerCase().includes(q)
        );
    });

    return (
        <>
            <p style={{ fontSize: "13px", color: "var(--text-muted, #888)", marginBottom: "16px" }}>
                {status}
            </p>
            <input
                className="amenity-search"
                type="search"
                placeholder="Search by name or ID…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
            />
            {filtered.length > 0 ? (
                <div className="amenity-grid">
                    {filtered.map((doc) => (
                        <AmenityCard key={doc.id} amenity={doc} />
                    ))}
                </div>
            ) : (
                <p>No documents found in the <code>Amenity</code> collection yet.</p>
            )}
        </>
    );
}

export default Amenity;
