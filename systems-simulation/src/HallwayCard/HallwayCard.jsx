import "./HallwayCard.css";

const CONGESTION_LABELS = ["Low", "Medium", "High"];

function HallwayCard({ nodeId, data }) {
    const { node, neighbors } = data ?? {};
    const neighborEntries = neighbors ? Object.entries(neighbors) : [];

    if (!node) return null;

    return (
        <div className="hallway-card">
            <div className="hallway-card__header">
                <span className="hallway-card__label">Node</span>
                <code className="hallway-card__code">{nodeId}</code>
            </div>

            <div className="hallway-card__coords">
                {node.lat.toFixed(6)}, {node.lng.toFixed(6)}
            </div>

            <div className="hallway-card__divider" />

            <span className="hallway-card__section-title">
                Neighbors ({neighborEntries.length})
            </span>

            {neighborEntries.length === 0 ? (
                <p className="hallway-card__empty">No neighbors</p>
            ) : (
                <ul className="hallway-card__neighbors">
                    {neighborEntries.map(([nId, nData]) => {
                        const congestionLabel = CONGESTION_LABELS[nData.congestion] ?? "Unknown";
                        const congestionMod = congestionLabel.toLowerCase();
                        return (
                            <li key={nId} className="hallway-card__neighbor">
                                <div className="hallway-card__neighbor-row">
                                    <code className="hallway-card__code hallway-card__code--sm">{nId}</code>
                                    <span className={`hallway-card__congestion hallway-card__congestion--${congestionMod}`}>
                                        ● {congestionLabel}
                                    </span>
                                </div>
                                <span className="hallway-card__coords">
                                    {nData.lat.toFixed(6)}, {nData.lng.toFixed(6)}
                                </span>
                            </li>
                        );
                    })}
                </ul>
            )}
        </div>
    );
}

export default HallwayCard;
