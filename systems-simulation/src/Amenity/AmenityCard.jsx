import { useState } from "react";
import { doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";
import "./AmenityCard.css";

function AmenityCard({ amenity }) {
    const {
        id,
        AmenityID,
        AmenityType,
        SubTypeName,
        Name,
        NodeID,
        Congestion,
        IsAccessible,
        WaitTime,
        LastUpdated
    } = amenity;

    const [waitTime, setWaitTime] = useState(WaitTime ?? 0);
    const [status, setStatus] = useState(IsAccessible ? "Open" : "Closed");
    const [updating, setUpdating] = useState(false);

    const congestionClass = `amenity-card__congestion amenity-card__congestion--${
        Congestion?.toLowerCase() ?? "unknown"
    }`;

    const committedStatus = IsAccessible ? "Open" : "Closed";
    const committedStatusClass = `amenity-card__status amenity-card__status--${committedStatus}`;

    const accessibility = SubTypeName == "neutral" ? true : false;

    const getCongestion = (wt) => {
        if (wt <= 1) return "Low";
        if (wt <= 3) return "Medium";
        return "High";
    };

    const handleUpdate = async () => {
        setUpdating(true);
        try {
            if (status === "Open")
                setWaitTime(0)

            await updateDoc(doc(db, "Amenity", AmenityID ?? id), {
                WaitTime: waitTime,
                Congestion: getCongestion(waitTime),
                IsAccessible: status === "Open",
                LastUpdated: Date.now(),
            });
        } catch (e) {
            console.error("Failed to update amenity:", e);
        } finally {
            setUpdating(false);
        }
    };

    return (
        <div className="amenity-card">
            <div className="amenity-card__header">
                <span className="amenity-card__type">{AmenityType}</span>
                <span
                    className={`amenity-card__accessible-badge amenity-card__accessible-badge--${
                        accessibility ? "yes" : "no"
                    }`}
                >
                    {accessibility ? "♿ Accessible" : "Not Accessible"}
                </span>
            </div>

            <h3 className="amenity-card__name">{Name}</h3>
            <p className="amenity-card__subtype">{SubTypeName}</p>

            <div className="amenity-card__divider" />

            <div className="amenity-card__row">
                <span className="amenity-card__label">Amenity ID</span>
                <code className="amenity-card__code">{AmenityID ?? id}</code>
            </div>

            <div className="amenity-card__row">
                <span className="amenity-card__label">Status</span>
                <span className={committedStatusClass}>● {committedStatus}</span>
            </div>

            <div className="amenity-card__row">
                <span className="amenity-card__label">Congestion</span>
                <span className={congestionClass}>● {Congestion}</span>
            </div>

            <div className="amenity-card__row">
                <span className="amenity-card__label">Last Updated</span>
                <span className="amenity-card__timestamp">
                    {LastUpdated
                        ? new Date(LastUpdated).toLocaleString()
                        : "—"}
                </span>
            </div>

            <div className="amenity-card__divider" />

            <div className="amenity-card__field">
                <label className="amenity-card__field-label" htmlFor={`status-${id}`}>
                    Status
                </label>
                <select
                    id={`status-${id}`}
                    className="amenity-card__input"
                    value={status}
                    onChange={(e) => setStatus(e.target.value)}
                >
                    <option value="Open">Open</option>
                    <option value="Closed">Closed</option>
                </select>
            </div>

            <div className="amenity-card__field">
                <label className="amenity-card__field-label" htmlFor={`wait-${id}`}>
                    Wait Time (min)
                </label>
                <input
                    id={`wait-${id}`}
                    className="amenity-card__input"
                    type="number"
                    value={waitTime}
                    onChange={(e) => setWaitTime(Number(e.target.value))}
                />
            </div>

            <button
                className="amenity-card__update-btn"
                onClick={handleUpdate}
                disabled={updating}
            >
                {updating ? "Updating…" : "Update Amenity"}
            </button>
        </div>
    );
}

export default AmenityCard;
