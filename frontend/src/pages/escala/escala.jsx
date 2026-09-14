import {useEffect, useMemo, useState} from "react";
import {useNavigate, useParams, useSearchParams} from "react-router";
import ScaleForm from "../../components/scale-form/scale-form.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {addDays, formatDate, formatWeekdayAndDate, mondayOf, toIsoDate} from "../../utils/date-format.js";
import {answersPayload, answersToValues} from "../../utils/scale-answers.js";
import styles from "./escala.module.css";

const CONNECTION_ERROR_MESSAGE = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

// os acompanhamentos semanais falam da ultima semana
const PERIOD_DAYS = 7;

// tela unica de preenchimento de escala (RF08 e RF21 a RF26)
//
// o formulario inteiro vem do servidor: titulo, itens, ancoras e
// opcoes. escala nova aparece aqui sem tela nova
export default function Escala() {
    const {slug} = useParams();
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const loggedUser = getLoggedUser();

    const today = toIsoDate(new Date());
    const chosenDate = searchParams.get("data");

    const [definition, setDefinition] = useState(null);
    const [responses, setResponses] = useState([]);
    const [sleepSchedule, setSleepSchedule] = useState(null);
    const [loadError, setLoadError] = useState(null);
    const [values, setValues] = useState({});
    const [selectedDay, setSelectedDay] = useState(chosenDate && chosenDate <= today ? chosenDate : today);
    const [periodEnd, setPeriodEnd] = useState(today);
    const [notice, setNotice] = useState(null);
    const [formError, setFormError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    const [reloadCount, setReloadCount] = useState(0);

    useEffect(() => {
        let isCurrentRequest = true;

        Promise.all([
            apiService.get("/escalas/definicoes/" + slug),
            apiService.get("/escalas/" + slug + "/respostas"),
        ])
            .then(([loadedDefinition, loadedResponses]) => {
                if (isCurrentRequest) {
                    setDefinition(loadedDefinition);
                    setResponses(loadedResponses);
                    setLoadError(null);
                    // o acompanhamento semanal fala da ultima semana, entao
                    // o periodo ja abre nela
                    if (loadedDefinition.fillMode === "PERIODO" && !chosenDate) {
                        setSelectedDay(toIsoDate(addDays(new Date(), -(PERIOD_DAYS - 1))));
                    }
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível abrir a escala. Confira sua internet e tente de novo.");
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [slug, chosenDate, reloadCount]);

    // a programacao de horarios do diario do sono, q o prescritor define
    // no acompanhamento. paciente sem acompanhamento n tem programacao
    useEffect(() => {
        if (slug !== "diario-sono") {
            return;
        }
        apiService.get("/pacientes/" + loggedUser.id + "/acompanhamento")
            .then(setSleepSchedule)
            .catch(() => setSleepSchedule(null));
    }, [slug, loggedUser.id]);

    const isDiary = definition ? definition.fillMode === "DIARIO" : false;
    const isPeriod = definition ? definition.fillMode === "PERIODO" : false;

    // a semana do dia escolhido, como a grade do formulario em papel
    const weekDays = useMemo(() => {
        const monday = mondayOf(new Date(selectedDay + "T00:00:00"));
        const days = [];
        for (let dayNumber = 0; dayNumber < 7; dayNumber = dayNumber + 1) {
            const dayIso = toIsoDate(addDays(monday, dayNumber));
            if (dayIso <= today) {
                days.push(dayIso);
            }
        }
        return days;
    }, [selectedDay, today]);

    const currentResponse = useMemo(
        () => responses.find((response) => response.periodStart === selectedDay) || null,
        [responses, selectedDay]);

    // trocar de dia carrega o q ja foi respondido naquele dia
    // dia em branco abre em branco: a tela nunca sugere valor (RN10)
    useEffect(() => {
        setValues(currentResponse ? answersToValues(currentResponse.answers) : {});
    }, [currentResponse]);

    // o recado de um dia n vale pro outro, mas salvar n pode apagar o
    // recado q acabou de aparecer
    useEffect(() => {
        setNotice(null);
        setFormError(null);
    }, [selectedDay]);

    const isReadOnly = currentResponse !== null && !currentResponse.editableByPatient;

    const changeAnswer = (key, value) => {
        setValues((currentValues) => ({...currentValues, [key]: value}));
    };

    const save = async (event) => {
        event.preventDefault();
        setFormError(null);
        setNotice(null);
        setIsSaving(true);
        try {
            const saved = await apiService.post("/escalas/" + slug + "/respostas", {
                periodStart: selectedDay,
                periodEnd: isPeriod ? periodEnd : selectedDay,
                answers: answersPayload(definition.items, values),
            });
            setNotice(saved.result ? "Respostas salvas. Resultado: " + saved.result : "Respostas salvas.");
            setReloadCount((currentCount) => currentCount + 1);
        } catch (requestError) {
            setFormError(requestError instanceof ApiError ? requestError.message : CONNECTION_ERROR_MESSAGE);
        } finally {
            setIsSaving(false);
        }
    };

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!definition) {
        return <p>Carregando a escala...</p>;
    }

    return (
        <section className={styles.page}>
            <header>
                <h1>{definition.title}</h1>
                <p className={styles.instruction}>{definition.instruction}</p>
            </header>

            {sleepSchedule && (sleepSchedule.sleepBedTime || sleepSchedule.sleepWakeTime) && (
                <p className={styles.schedule}>
                    Programação do seu prescritor: dormir às {sleepSchedule.sleepBedTime || "—"} e
                    levantar às {sleepSchedule.sleepWakeTime || "—"}.
                </p>
            )}

            {isDiary && (
                <section>
                    <h2 className={styles.sectionTitle}>Dia da semana</h2>
                    <div className={styles.days} role="group">
                        {weekDays.map((dayIso) => (
                            <button
                                key={dayIso}
                                type="button"
                                className={dayIso === selectedDay ? styles.dayChosen : styles.day}
                                aria-pressed={dayIso === selectedDay}
                                onClick={() => setSelectedDay(dayIso)}
                            >
                                <span>{formatWeekdayAndDate(dayIso)}</span>
                                <span className={styles.dayMark}>
                                    {responses.some((response) => response.periodStart === dayIso)
                                        ? "preenchido"
                                        : "em branco"}
                                </span>
                            </button>
                        ))}
                    </div>
                </section>
            )}

            <form className={styles.form} onSubmit={save}>
                {!isDiary && (
                    <div className={styles.period}>
                        <label htmlFor="periodStart">
                            {isPeriod ? "Início do período" : "Data da avaliação"}
                        </label>
                        <input
                            id="periodStart"
                            type="date"
                            max={today}
                            value={selectedDay}
                            disabled={isReadOnly}
                            onChange={(event) => setSelectedDay(event.target.value)}
                        />
                        {isPeriod && (
                            <>
                                <label htmlFor="periodEnd">Fim do período</label>
                                <input
                                    id="periodEnd"
                                    type="date"
                                    value={periodEnd}
                                    disabled={isReadOnly}
                                    onChange={(event) => setPeriodEnd(event.target.value)}
                                />
                            </>
                        )}
                    </div>
                )}

                {isReadOnly && (
                    <p className="aviso">
                        {currentResponse.annulled
                            ? "Esta resposta foi anulada pelo prescritor e fica no histórico como está."
                            : "O prescritor já analisou esta resposta. Para corrigir, fale com ele."}
                    </p>
                )}

                {currentResponse && currentResponse.result && (
                    <p className={styles.result}>Resultado: {currentResponse.result}</p>
                )}

                {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}
                {notice && <p className="aviso" role="status">{notice}</p>}

                <ScaleForm
                    items={definition.items}
                    values={values}
                    onChange={changeAnswer}
                    disabled={isReadOnly || isSaving}
                />

                <div className={styles.actions}>
                    <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                        Voltar
                    </button>
                    {!isReadOnly && (
                        <button type="submit" className="button" disabled={isSaving}>
                            {isSaving ? "Salvando..." : "Salvar respostas"}
                        </button>
                    )}
                </div>
            </form>

            {isPeriod && currentResponse && (
                <p className={styles.periodNote}>
                    Período respondido: {formatDate(currentResponse.periodStart)} a {formatDate(currentResponse.periodEnd)}.
                </p>
            )}
        </section>
    );
}
