import UrgencyNotice from "../urgency-notice/urgency-notice.jsx";
import {answersPayload, formatAnswer} from "../../utils/scale-answers.js";
import {stepsOf} from "./scale-steps.js";
import styles from "./scale-form.module.css";

// desenha os itens de qualquer escala a partir da definicao q veio da api
//
// a tela n sabe o nome de escala nenhuma: o tipo do item diz q campo
// usar, e as ancoras as opcoes e as faixas vem prontas do servidor (RNF08)
//
// o erro de campo q a api manda (fieldErrors, pela chave do item) aparece no proprio item
// na tela do paciente o texto livre vem com o aviso de q o sistema n eh urgencia (showsUrgencyNotice, R1)
// o item q falta ou com erro fica com aria-invalid, e a tela leva o foco pro primeiro (useFocusFirstError)
//
// com o step (a tela do celular q a pagina da escala controla) so aparece a pergunta daquela tela
// cada item diz a tela dele no data-step, e o data-step-focus marca onde o foco vai qnd a tela muda
export default function ScaleForm({items, values, onChange, disabled, missingKeys = [], bands = [],
    fieldErrors = {}, showsUrgencyNotice = false, step = null}) {
    // o valor q o sistema calcula n eh pergunta, entao fica fora da conta e vai junto da pergunta de antes
    const steps = stepsOf(items);
    const answered = Object.keys(answersPayload(items, values)).length;
    const stepOfItem = {};
    steps.forEach((stepItems, index) => stepItems.forEach((item) => {
        stepOfItem[item.key] = index;
    }));
    const shownItems = step === null ? items : (steps[step] || []);

    return (
        <div className={styles.form}>
            {steps.length > 0 && <ScaleProgress answered={answered} total={steps.length} step={step}/>}
            <ol className={styles.items}>
                {shownItems.map((item) => {
                    const isMarked = missingKeys.includes(item.key) || Boolean(fieldErrors[item.key]);
                    return (
                        <li
                            key={item.key}
                            className={isMarked ? styles.item + " " + styles.itemMissing : styles.item}
                            data-step={stepOfItem[item.key]}
                        >
                            <ScaleField
                                item={item}
                                bands={bandsOfItem(item, bands)}
                                values={values}
                                onChange={onChange}
                                disabled={disabled}
                                isMarked={isMarked}
                                error={fieldErrors[item.key]}
                                showsUrgencyNotice={showsUrgencyNotice}
                                isStep={step !== null}
                            />
                        </li>
                    );
                })}
            </ol>
        </div>
    );
}

// quantos itens a pessoa ja respondeu, preso no alto enquanto ela rola o formulario
// o data-top-bar faz o campo com foco atras dele descer (utils/focus-above-bars.js)
// o texto eh um so, entao o leitor de tela so ouve qnd a conta muda e n a cada letra do texto livre
// no celular ele diz tbm em q pergunta a pessoa esta, e ai a troca de tela eh anunciada
function ScaleProgress({answered, total, step}) {
    let text = answered + " de " + total + " respondidas";
    if (step !== null) {
        text = "Pergunta " + (step + 1) + " de " + total + " · " + text;
    }
    return (
        <div className={styles.progress} data-top-bar="">
            <p className={styles.progressText} role="status">{text}</p>
            <div className={styles.progressTrack} aria-hidden="true">
                <div className={styles.progressFill} style={{width: Math.round(answered / total * 100) + "%"}}/>
            </div>
        </div>
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

function ScaleField({item, bands, values, onChange, disabled, isMarked, error, showsUrgencyNotice, isStep}) {
    const value = values[item.key] ?? "";
    const change = (newValue) => onChange(item.key, newValue);

    if (item.type === "CALCULADO") {
        return <ComputedField item={item} value={value}/>;
    }
    if (item.type === "NOTA" || item.type === "ESCOLHA" || item.type === "SIM_NAO") {
        return (
            <ChoiceField item={item} bands={bands} value={value} onChange={change} disabled={disabled}
                isMarked={isMarked} error={error} isStep={isStep}/>
        );
    }
    return (
        <TypedField item={item} value={value} onChange={change} disabled={disabled} isMarked={isMarked}
            error={error} showsUrgencyNotice={showsUrgencyNotice} isStep={isStep}/>
    );
}

// o id liga o erro ao campo pelo aria-describedby, entao o leitor de tela le junto
function errorIdOf(item) {
    return item.key + "-error";
}

function FieldError({item, error}) {
    return error ? <span id={errorIdOf(item)} className={styles.error}>{error}</span> : null;
}

// o sistema calcula e a pessoa n preenche
function ComputedField({item, value}) {
    return (
        <div className={styles.field}>
            <Label item={item}/>
            <p className={styles.computed}>
                {value === "" ? "o sistema calcula quando você salvar" : formatAnswer(item, value)}
            </p>
        </div>
    );
}

// no celular o foco da tela nova vai pro grupo inteiro, e o leitor de tela le a pergunta
// o grupo travado (resposta ja analisada) n recebe foco, entao ai o foco vai pra legenda
function ChoiceField({item, bands, value, onChange, disabled, isMarked, error, isStep}) {
    const focusesGroup = isStep && !disabled;
    const focusesLegend = isStep && disabled;
    // toque errado no celular n pode virar dado clinico (RN10)
    // o botao some dps de limpar, entao o foco volta pra primeira opcao
    const clearAnswer = (event) => {
        event.currentTarget.closest("fieldset").querySelector("input").focus();
        onChange("");
    };

    let choices = <OptionChoices item={item} value={value} onChange={onChange} invalid={isMarked}/>;
    if (item.type === "NOTA" && bands.length > 0) {
        choices = <BandedScoreChoices item={item} bands={bands} value={value} onChange={onChange} invalid={isMarked}/>;
    } else if (item.type === "NOTA") {
        choices = <ScoreChoices item={item} value={value} onChange={onChange} invalid={isMarked}/>;
    }

    return (
        <fieldset
            className={styles.field}
            disabled={disabled}
            aria-describedby={error ? errorIdOf(item) : undefined}
            tabIndex={focusesGroup ? -1 : undefined}
            data-step-focus={focusesGroup ? "" : undefined}
        >
            <legend
                className={styles.legend}
                tabIndex={focusesLegend ? -1 : undefined}
                data-step-focus={focusesLegend ? "" : undefined}
            >
                <Label item={item}/>
            </legend>
            {choices}
            <FieldError item={item} error={error}/>
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

// texto livre hora ou numero digitado
// no celular o foco da tela nova vai pro proprio campo, e com o campo travado vai pro rotulo dele
function TypedField({item, value, onChange, disabled, isMarked, error, showsUrgencyNotice, isStep}) {
    const focusesField = isStep && !disabled;
    const focusesLabel = isStep && disabled;
    const noticeId = item.key + "-urgencia";
    const hasNotice = showsUrgencyNotice && item.type === "TEXTO";
    // o erro e o aviso de urgencia lidos junto com o campo
    const describedBy = [error ? errorIdOf(item) : null, hasNotice ? noticeId : null]
        .filter((id) => id !== null)
        .join(" ");
    return (
        <div className={styles.field}>
            <label
                className={styles.labelBox}
                htmlFor={item.key}
                tabIndex={focusesLabel ? -1 : undefined}
                data-step-focus={focusesLabel ? "" : undefined}
            >
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
                    aria-invalid={isMarked ? "true" : undefined}
                    aria-describedby={describedBy === "" ? undefined : describedBy}
                    data-step-focus={focusesField ? "" : undefined}
                    onChange={(event) => onChange(event.target.value)}
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
                    aria-invalid={isMarked ? "true" : undefined}
                    aria-describedby={error ? errorIdOf(item) : undefined}
                    data-step-focus={focusesField ? "" : undefined}
                    onChange={(event) => onChange(event.target.value)}
                />
            )}
            <FieldError item={item} error={error}/>
            {hasNotice && <UrgencyNotice id={noticeId}/>}
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
function ScoreChoices({item, value, onChange, invalid}) {
    return (
        <>
            <div className={styles.scores} role="group">
                {scoresBetween(item.minValue, item.maxValue).map((score) => (
                    <ScoreOption key={score} item={item} score={score} value={value} onChange={onChange}
                        invalid={invalid}/>
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
function BandedScoreChoices({item, bands, value, onChange, invalid}) {
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
                                <ScoreOption key={score} item={item} score={score} value={value} onChange={onChange}
                                    invalid={invalid}/>
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

function ScoreOption({item, score, value, onChange, invalid}) {
    return (
        <label className={styles.score}>
            <input
                type="radio"
                name={item.key}
                value={score}
                checked={String(value) === String(score)}
                aria-invalid={invalid ? "true" : undefined}
                onChange={() => onChange(String(score))}
            />
            <span>{score}</span>
        </label>
    );
}

function OptionChoices({item, value, onChange, invalid}) {
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
                        aria-invalid={invalid ? "true" : undefined}
                        onChange={() => onChange(option.value)}
                    />
                    <span>{option.label}</span>
                </label>
            ))}
        </div>
    );
}
