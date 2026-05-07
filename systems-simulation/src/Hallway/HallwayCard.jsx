import { useState } from "react";
import { ref, update } from "firebase/database";
import { rtdb } from "../firebase";
import "./HallwayCard.css";

const CONGESTION_LABELS = ["Low", "Medium", "High"];

function HallwayCard({ nodeId, data }) {
    const { name, id, congestion, isOpen, coordinates } = data ?? {};

    const [statusOpen, setStatusOpen] = useState(isOpen ?? true);
    const [congestionIdx, setCongestionIdx] = useState(Number(congestion ?? 0));
    const [updating, setUpdating] = useState(false);

    const committedLabel = CONGESTION_LABELS[Number(congestion)] ?? "Unknown";
    const committedMod = committedLabel.toLowerCase();
    const committedStatus = isOpen ? "open" : "closed";

    const [start, end] = coordinates ?? [];

    const handleUpdate = async () => {
        setUpdating(true);
        try {
            await update(ref(rtdb, `MapData/CurrentGraph/edges/${nodeId}`), {
                isOpen: statusOpen,
                congestion: congestionIdx,
            });
        } catch (e) {
            console.error("Failed to update edge:", e);
        } finally {
            setUpdating(false);
        }
    };

    return (
        <div className="hallway-card">
            <div className="hallway-card__header">
                <span className="hallway-card__label">Edge</span>
                <code className="hallway-card__code">{id ?? nodeId}</code>
            </div>

            {name && <span className="hallway-card__name">{name}</span>}

            {(start || end) && (
                <>
                    <div className="hallway-card__divider" />
                    <span className="hallway-card__section-title">Coordinates</span>
                    {start && (
                        <div className="hallway-card__coord-row">
                            <span className="hallway-card__coord-label">From</span>
                            <span className="hallway-card__coords">
                                {start.lat.toFixed(6)}, {start.lng.toFixed(6)}
                            </span>
                        </div>
                    )}
                    {end && (
                        <div className="hallway-card__coord-row">
                            <span className="hallway-card__coord-label">To</span>
                            <span className="hallway-card__coords">
                                {end.lat.toFixed(6)}, {end.lng.toFixed(6)}
                            </span>
                        </div>
                    )}
                </>
            )}

            <div className="hallway-card__divider" />

            <div className="hallway-card__row">
                <span className="hallway-card__coord-label">Status</span>
                <span className={`hallway-card__status hallway-card__status--${committedStatus}`}>
                    ● {isOpen ? "Open" : "Closed"}
                </span>
            </div>

            <div className="hallway-card__row">
                <span className="hallway-card__coord-label">Congestion</span>
                <span className={`hallway-card__congestion hallway-card__congestion--${committedMod}`}>
                    ● {committedLabel}
                </span>
            </div>

            <div className="hallway-card__divider" />

            <div className="hallway-card__field">
                <label className="hallway-card__field-label" htmlFor={`status-${nodeId}`}>
                    Status
                </label>
                <select
                    id={`status-${nodeId}`}
                    className="hallway-card__input"
                    value={statusOpen ? "open" : "closed"}
                    onChange={(e) => setStatusOpen(e.target.value === "open")}
                >
                    <option value="open">Open</option>
                    <option value="closed">Closed</option>
                </select>
            </div>

            <div className="hallway-card__field">
                <label className="hallway-card__field-label" htmlFor={`congestion-${nodeId}`}>
                    Congestion
                </label>
                <input
                    id={`congestion-${nodeId}`}
                    className="hallway-card__input"
                    type="number"
                    value={congestionIdx}
                    onChange={(e) => setCongestionIdx(Number(e.target.value))}
                />
            </div>

            <button
                className="hallway-card__update-btn"
                onClick={handleUpdate}
                disabled={updating}
            >
                {updating ? "Updating…" : "Update Edge"}
            </button>
        </div>
    );
}

export default HallwayCard;
