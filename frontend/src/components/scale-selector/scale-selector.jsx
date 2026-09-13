import "./scale-selector.css";

// seletor de nota das escalas.
//
// antes ele desenhava 7 botoes fixos valendo de 1 a 7, mas cada
// instrumento tem a sua faixa: o acompanhamento semanal vai de 0 a 10,
// o diario de sono de 0 a 5 e o hamilton de 0 a 4. como os tres usavam
// o mesmo componente, os tres gravavam valor fora da escala, e marcar
// "ausente" no hamilton valia 1 em vez de 0.
// agora a faixa vem de quem usa, e o numero aparece no botao
export default function ScaleSelector({
                                          leftLabel,
                                          rightLabel,
                                          value,
                                          onChangeValue,
                                          minValue = 0,
                                          maxValue = 10,
                                      }) {
    const notas = [];
    for (let nota = minValue; nota <= maxValue; nota++) {
        notas.push(nota);
    }

    return (
        <div className="scale-selector">
            <span>{leftLabel}</span>
            <div className="scale-selector__content">
                {notas.map((nota) => (
                    <button
                        type="button"   // defini para evitar salvar nesse bottom
                        key={nota}
                        className={value === nota ? "selected" : ""}
                        onClick={() => onChangeValue(nota)}
                    >
                        {nota}
                    </button>
                ))}
            </div>
            <span>{rightLabel}</span>
        </div>
    );
}
