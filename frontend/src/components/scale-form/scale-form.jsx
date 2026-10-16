import {formatAnswer} from "../../utils/scale-answers.js";
import styles from "./scale-form.module.css";

// desenha os itens de qualquer escala a partir da definicao q veio da api
//
// a tela n sabe o nome de escala nenhuma: o tipo do item diz q campo
// usar, e as ancoras as opcoes e as faixas vem prontas do servidor (RNF08)
export default function ScaleForm({items, values, onChange, disabled, missingKeys = [], bands = []}) {
    return (
        <ol className={styles.items}>
            {items.map((item) => (
                <li
                    key={item.key}
                    className={missingKeys.includes(item.key) ? styles.item + " " + styles.itemMissing : styles.item}
                >
                    <ScaleField
                        item={item}
                        bands={bandsOfItem(item, bands)}
                        values={values}
                        onChange={onChange}
                        disabled={disabled}
                    />
                </li>
            ))}
        </ol>
    );
}

// a faixa da escala so vale pro item q vai de ponta a ponta dela, como a
// intensidade da dor, q eh o proprio escore. no hamilton a faixa eh do total
// de 0 a 56 e o item vai de 0 a 4, entao o item fica com a regua simples
function bandsOfItem(item, bands) {
    if (item.type !== "NOTA" || !bands || bands.length === 0) {
        return [];
    }
    const insideTheItem = bands.every((band) => band.minScore >= item.minValue && band.maxScore <= item.maxValue);
    const eachScoreInOneBand = scoresBetween(item.minValue, item.maxValue).every((score) =>
        bands.filter((band) => score >= band.minScore && score <= band.maxScore).length === 1);
    if (!insideTheItem || !eachScoreInOneBand) {
        return [];
    }
    return [...bands].sort((first, second) => first.minScore - second.minScore);
}

function scoresBetween(minValue, maxValue) {
    const scores = [];
    for (let score = minValue; score <= maxValue; score = score + 1) {
        scores.push(score);
    }
    return scores;
}

function ScaleField({item, bands, values, onChange, disabled}) {
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
        // toque errado no celular n pode virar dado clinico (RN10)
        // o botao some dps de limpar, entao o foco volta pra primeira opcao
        const clearAnswer = (event) => {
            event.currentTarget.closest("fieldset").querySelector("input").focus();
            change("");
        };

        let choices = <OptionChoices item={item} value={value} onChange={change}/>;
        if (item.type === "NOTA" && bands.length > 0) {
            choices = <BandedScoreChoices item={item} bands={bands} value={value} onChange={change}/>;
        } else if (item.type === "NOTA") {
            choices = <ScoreChoices item={item} value={value} onChange={change}/>;
        }

        return (
            <fieldset className={styles.field} disabled={disabled}>
                <legend className={styles.legend}>
                    <Label item={item}/>
                </legend>
                {choices}
                {value !== "" && !disabled && (
                    <button
                        type="button"
                        className={"button-tertiary button-small " + styles.clear}
                        aria-label={"Limpar resposta de " + item.label}
                        onClick={clearAnswer}
                    >
                        Limpar resposta
                    </button>
                )}
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
    return (
        <>
            <div className={styles.scores} role="group">
                {scoresBetween(item.minValue, item.maxValue).map((score) => (
                    <ScoreOption key={score} item={item} score={score} value={value} onChange={onChange}/>
                ))}
            </div>
            <p className={styles.anchors}>
                <span>{item.minValue} {item.lowAnchor}</span>
                <span>{item.maxValue} {item.highAnchor}</span>
            </p>
        </>
    );
}

// a regua desenhada como a escala visual do papel, com cada faixa nomeada e
// colorida em cima das notas dela (RF25). o nome fica escrito, entao a cor n
// eh o unico sinal. as pontas ja estao no nome das faixas e as ancoras saem
function BandedScoreChoices({item, bands, value, onChange}) {
    return (
        <div className={styles.bands}>
            {bands.map((band, index) => {
                const nameId = item.key + "-faixa-" + index;
                return (
                    <div
                        key={band.label}
                        role="group"
                        aria-labelledby={nameId}
                        className={styles.band + " " + toneOf(index, bands.length, item.higherIsBetter)}
                        style={{flexGrow: band.maxScore - band.minScore + 1}}
                    >
                        <p id={nameId} className={styles.bandName}>
                            <span>{band.label}</span>{" "}
                            <span className={styles.bandRange}>{band.minScore} a {band.maxScore}</span>
                        </p>
                        <div className={styles.bandScores}>
                            {scoresBetween(band.minScore, band.maxScore).map((score) => (
                                <ScoreOption key={score} item={item} score={score} value={value} onChange={onChange}/>
                            ))}
                        </div>
                    </div>
                );
            })}
        </div>
    );
}

// as cores seguem a escala de dor do papel: azul na ponta boa, vermelho na ponta
// ruim e ambar no meio. no papel o meio vai do verde ao ambar, aqui ficou um ambar so.
// o lado bom vem do item, pq em outra escala o 10 pode ser o bom
function toneOf(index, total, higherIsBetter) {
    const isFirst = index === 0;
    const isLast = index === total - 1;
    if (total === 1 || (!isFirst && !isLast)) {
        return styles.bandMiddle;
    }
    const isGoodEnd = higherIsBetter ? isLast : isFirst;
    return isGoodEnd ? styles.bandGood : styles.bandBad;
}

function ScoreOption({item, score, value, onChange}) {
    return (
        <label className={styles.score}>
            <input
                type="radio"
                name={item.key}
                value={score}
                checked={String(value) === String(score)}
                onChange={() => onChange(String(score))}
            />
            <span>{score}</span>
        </label>
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
