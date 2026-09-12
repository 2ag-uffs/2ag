import "./scale-selector.css";

export default function ScaleSelector({
                                          leftLabel,
                                          rightLabel,
                                          value,
                                          onChangeValue,
                                      }) {
    return (
        <div className="scale-selector">
            <span>{leftLabel}</span>
            <div className="scale-selector__content">
                {new Array(7).fill(0).map((_, index) => (
                    <button
                        type="button"   // defini para evitar salvar nesse bottom
                        key={index}
                        className={value === index + 1 ? "selected" : ""}
                        onClick={() => onChangeValue(index + 1)}
                    />
                ))}
            </div>
            <span>{rightLabel}</span>
        </div>
    );
}