import {formatAnswer} from "../../utils/scale-answers.js";
import styles from "./scale-form.module.css";

// desenha os itens de qualquer escala a partir da definicao q veio da api
//
// a tela n sabe o nome de escala nenhuma: o tipo do item diz q campo
// usar, e as ancoras e as opcoes vem prontas do servidor (RNF08)
export default function ScaleForm({items, values, onChange, disabled}) {
    return (
        <ol className={styles.items}>
            {items.map((item) => (
                <li key={item.key} className={styles.item}>
                    <ScaleField item={item} values={values} onChange={onChange} disabled={disabled}/>
                </li>
            ))}
        </ol>
    );
}

function ScaleField({item, values, onChange, disabled}) {
    const value = values[item.key] ?? "";
    const change = (newValue) => onChange(item.key, newValue);

    // o sistema calcula e a pessoa n preenche
    if (item.type === "CALCULADO") {
        return (
            <div className={styles.field}>
                <Label item={item}/>
                <p className={styles.computed}>
                    {value === "" ? "o sistema calcula quando você salvar" : formatAnswer(item, value)}
                </p>
            </div>
        );
    }

    if (item.type === "NOTA" || item.type === "ESCOLHA" || item.type === "SIM_NAO") {
        return (
            <fieldset className={styles.field} disabled={disabled}>
                <legend className={styles.legend}>
                    <Label item={item}/>
                </legend>
                {item.type === "NOTA"
                    ? <ScoreChoices item={item} value={value} onChange={change}/>
                    : <OptionChoices item={item} value={value} onChange={change}/>}
            </fieldset>
        );
    }

    return (
        <div className={styles.field}>
            <label className={styles.labelBox} htmlFor={item.key}>
                <Label item={item}/>
            </label>
            {item.type === "TEXTO" ? (
                <textarea
                    id={item.key}
                    className={styles.textarea}
                    rows={3}
                    maxLength={2000}
                    value={value}
                    disabled={disabled}
                    onChange={(event) => change(event.target.value)}
                />
            ) : (
                <input
                    id={item.key}
                    className={styles.input}
                    type={item.type === "HORA" ? "time" : "number"}
                    min={item.type === "HORA" ? undefined : 0}
                    max={item.type === "HORAS" ? 24 : undefined}
                    step={item.type === "HORAS" ? 0.5 : 1}
                    value={value}
                    disabled={disabled}
                    onChange={(event) => change(event.target.value)}
                />
            )}
        </div>
    );
}

function Label({item}) {
    return (
        <>
            <span className={styles.label}>{item.label}</span>
            {item.help && <span className={styles.help}>{item.help}</span>}
        </>
    );
}

// a regua de notas, com as ancoras nas pontas pra dizer o q eh 0 e o q
// eh o maximo. sem isso o numero sozinho n quer dizer nada
function ScoreChoices({item, value, onChange}) {
    const scores = [];
    for (let score = item.minValue; score <= item.maxValue; score = score + 1) {
        scores.push(score);
    }

    return (
        <>
            <div className={styles.scores} role="group">
                {scores.map((score) => (
                    <label key={score} className={styles.score}>
                        <input
                            type="radio"
                            name={item.key}
                            value={score}
                            checked={String(value) === String(score)}
                            onChange={() => onChange(String(score))}
                        />
                        <span>{score}</span>
                    </label>
                ))}
            </div>
            <p className={styles.anchors}>
                <span>{item.minValue} {item.lowAnchor}</span>
                <span>{item.maxValue} {item.highAnchor}</span>
            </p>
        </>
    );
}

function OptionChoices({item, value, onChange}) {
    const options = item.type === "SIM_NAO"
        ? [{value: "true", label: "Sim"}, {value: "false", label: "Não"}]
        : item.options.map((option) => ({value: String(option.value), label: option.label}));

    return (
        <div className={styles.options}>
            {options.map((option) => (
                <label key={option.value} className={styles.option}>
                    <input
                        type="radio"
                        name={item.key}
                        value={option.value}
                        checked={String(value) === option.value}
                        onChange={() => onChange(option.value)}
                    />
                    <span>{option.label}</span>
                </label>
            ))}
        </div>
    );
}
