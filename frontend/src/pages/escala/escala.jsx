import {useEffect, useMemo, useState} from "react";
import {useNavigate, useParams, useSearchParams} from "react-router";
import {FiCheck, FiMoon} from "react-icons/fi";
import {FormActions} from "../../components/form-section/form-section.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import ScaleForm from "../../components/scale-form/scale-form.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError, getLoggedUser} from "../../services/api.js";
import {addDays, formatDate, formatWeekdayAndDate, mondayOf, toIsoDate} from "../../utils/date-format.js";
import {answersPayload, answersToValues, missingItemsOf, requiredItemsOf} from "../../utils/scale-answers.js";
import styles from "./escala.module.css";

const CONNECTION_ERROR_MESSAGE = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

// os acompanhamentos semanais falam da ultima semana
const PERIOD_DAYS = 7;

// pode ter duas pendentes da mesma escala (a antiga q voltou pra pendente e a atual)
// vale a q cobre hoje, pq o servidor liga cada dia na tarefa q cobre ele, e sem nenhuma a q comecou por ultimo
function openTaskOf(pendingTasks, slug, today) {
    const tasksOfScale = pendingTasks.filter((task) => task.path === "/escalas/" + slug);
    const taskOfToday = tasksOfScale.find((task) => task.periodStart <= today && task.periodEnd >= today);
    if (taskOfToday) {
        return taskOfToday;
    }
    let latestTask = null;
    for (const task of tasksOfScale) {
        if (latestTask === null || task.periodStart > latestTask.periodStart) {
            latestTask = task;
        }
    }
    return latestTask;
}

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
    const [openTask, setOpenTask] = useState(null);
    const [loadError, setLoadError] = useState(null);
    // o q a paciente mexeu fica guardado por dia ate salvar
    const [editsByDay, setEditsByDay] = useState({});
    const [selectedDay, setSelectedDay] = useState(chosenDate && chosenDate <= today ? chosenDate : today);
    const [periodEndByDay, setPeriodEndByDay] = useState({});
    // o inicio do periodo q a paciente mexeu na correcao, antes de salvar
    const [periodStartByDay, setPeriodStartByDay] = useState({});
    const [notice, setNotice] = useState(null);
    const [formError, setFormError] = useState(null);
    const [isSaving, setIsSaving] = useState(false);
    // itens da escala pontuada q ficaram em branco no ultimo envio
    const [missingKeys, setMissingKeys] = useState([]);
    const [reloadCount, setReloadCount] = useState(0);

    useEffect(() => {
        let isCurrentRequest = true;

        Promise.all([
            apiService.get("/scales/definitions/" + slug),
            apiService.get("/scales/" + slug + "/responses"),
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
        apiService.get("/patients/" + loggedUser.id + "/treatment-protocol")
            .then(setSleepSchedule)
            .catch(() => setSleepSchedule(null));
    }, [slug, loggedUser.id]);

    const isDiary = definition ? definition.fillMode === "DIARIO" : false;
    const isPeriod = definition ? definition.fillMode === "PERIODO" : false;

    // a tarefa aberta dessa escala, pq a grade do diario precisa seguir a janela dela
    useEffect(() => {
        if (!isDiary) {
            return;
        }
        apiService.get("/patients/" + loggedUser.id + "/scales/overview")
            .then((page) => setOpenTask(openTaskOf(page.pending, slug, today)))
            .catch(() => setOpenTask(null));
    }, [isDiary, slug, loggedUser.id, today]);

    const insideOpenTask = openTask !== null
        && selectedDay >= openTask.periodStart
        && selectedDay <= openTask.periodEnd;

    // a grade cobre o periodo inteiro da tarefa aberta, pro paciente preencher de uma vez os dias q
    // esqueceu, mesmo qnd o periodo passa de uma semana (issue 71). fora da tarefa fica a semana do
    // dia escolhido, como no papel, pq eh por ela q o "ver e corrigir" do historico chega no dia antigo
    // dia q ainda n chegou nunca aparece
    const gridDays = useMemo(() => {
        const firstDay = insideOpenTask
            ? openTask.periodStart
            : toIsoDate(mondayOf(new Date(selectedDay + "T00:00:00")));
        const periodLastDay = insideOpenTask
            ? openTask.periodEnd
            : toIsoDate(addDays(new Date(firstDay + "T00:00:00"), 6));
        const lastDay = periodLastDay < today ? periodLastDay : today;

        const days = [];
        let dayIso = firstDay;
        while (dayIso <= lastDay) {
            days.push(dayIso);
            const nextDay = toIsoDate(addDays(new Date(dayIso + "T00:00:00"), 1));
            // se a conta do dia seguinte n andar a grade para aqui em vez de travar a tela
            if (nextDay <= dayIso) {
                break;
            }
            dayIso = nextDay;
        }
        return days;
    }, [selectedDay, today, openTask, insideOpenTask]);

    // resposta anulada fica so no historico: o dia dela abre em branco pro paciente responder de novo
    const currentResponse = useMemo(
        () => responses.find((response) => response.periodStart === selectedDay && !response.annulled) || null,
        [responses, selectedDay]);
    const hasAnnulledResponse = currentResponse === null
        && responses.some((response) => response.periodStart === selectedDay && response.annulled);

    // cada dia mostra o q ja foi respondido nele ou o q a paciente esta mexendo
    // dia em branco abre em branco e a tela nunca sugere valor (RN10)
    const savedValues = currentResponse ? answersToValues(currentResponse.answers) : {};
    const values = editsByDay[selectedDay] || savedValues;
    // o periodo ja respondido abre com o fim q foi salvo e n com hoje
    const periodEnd = periodEndByDay[selectedDay] || (currentResponse ? currentResponse.periodEnd : today);

    // o recado de um dia n vale pro outro entao trocar de dia limpa os avisos
    // salvar n passa por aqui e o recado de salvo continua na tela
    const changeDay = (dayIso) => {
        setSelectedDay(dayIso);
        setNotice(null);
        setFormError(null);
        setMissingKeys([]);
    };

    const changePeriodEnd = (dayIso) => {
        setPeriodEndByDay((current) => ({...current, [selectedDay]: dayIso}));
    };

    const isReadOnly = currentResponse !== null && !currentResponse.editableByPatient;

    // a correcao de escala de periodo abre pela data (?data=) e mexe na resposta q ja existe:
    // as datas viram campo dela e salvar atualiza ela, em vez de criar outra no dia novo
    // e deixar a errada no historico. no diario o dia eh o da grade e a correcao segue
    // pelo envio do dia, q o servidor ja trata como correcao
    const isCorrecting = !isDiary && chosenDate !== null && currentResponse !== null && !isReadOnly;
    const periodStart = isCorrecting
        ? (periodStartByDay[selectedDay] || currentResponse.periodStart)
        : selectedDay;

    const changePeriodStart = (dayIso) => {
        setPeriodStartByDay((current) => ({...current, [selectedDay]: dayIso}));
    };

    const changeAnswer = (key, value) => {
        setEditsByDay((current) => ({
            ...current,
            [selectedDay]: {...(current[selectedDay] || savedValues), [key]: value},
        }));
        setMissingKeys((currentKeys) => currentKeys.filter((currentKey) => currentKey !== key));
    };

    const save = async (event) => {
        event.preventDefault();
        setFormError(null);
        setNotice(null);

        const missingItems = missingItemsOf(requiredItemsOf(definition), values);
        if (missingItems.length > 0) {
            setMissingKeys(missingItems.map((item) => item.key));
            return;
        }

        setIsSaving(true);
        try {
            const body = {
                periodStart,
                periodEnd: isPeriod ? periodEnd : periodStart,
                answers: answersPayload(definition.items, values),
            };
            const saved = isCorrecting
                ? await apiService.put("/scales/responses/" + currentResponse.id, body)
                : await apiService.post("/scales/" + slug + "/responses", body);
            setNotice(saved.result ? "Respostas salvas. Resultado: " + saved.result : "Respostas salvas.");
            // a resposta salva entra na lista na hora e o rascunho daquele dia sai
            setResponses((current) => [saved, ...current.filter((response) => response.id !== saved.id
                && response.periodStart !== saved.periodStart)]);
            setEditsByDay((current) => ({...current, [selectedDay]: null}));
            setPeriodEndByDay((current) => ({...current, [selectedDay]: null}));
            setPeriodStartByDay((current) => ({...current, [selectedDay]: null}));
            // a tela segue na resposta salva ja em modo de correcao: mexer na data dps
            // de salvar corrige essa resposta, em vez de abrir outro dia em branco
            if (!isDiary && chosenDate !== saved.periodStart) {
                setSelectedDay(saved.periodStart);
                navigate("/escalas/" + slug + "?data=" + saved.periodStart, {replace: true});
            } else {
                setReloadCount((currentCount) => currentCount + 1);
            }
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
        return <SkeletonPage cards={2}/>;
    }

    // o meem eh aplicado pelo prescritor na consulta, entao o paciente n ganha o formulario (RN09)
    if (!definition.filledByPatient) {
        return (
            <section className={styles.page}>
                <PageHeader title={definition.title}/>
                <p className="aviso">Esta escala é aplicada pelo prescritor durante a consulta.</p>
            </section>
        );
    }

    const hasRequiredItems = requiredItemsOf(definition).length > 0;
    const missingLabels = definition.items
        .filter((item) => missingKeys.includes(item.key))
        .map((item) => item.label);

    return (
        <section className={styles.page}>
            <PageHeader title={definition.title} subtitle={definition.instruction}/>

            {sleepSchedule && (sleepSchedule.sleepBedTime || sleepSchedule.sleepWakeTime) && (
                <p className={styles.schedule}>
                    <FiMoon aria-hidden="true"/>
                    {/* a api manda hh:mm:ss e aqui so interessa hh:mm */}
                    Programação do seu prescritor: dormir às {sleepSchedule.sleepBedTime
                        ? sleepSchedule.sleepBedTime.slice(0, 5) : "—"} e
                    levantar às {sleepSchedule.sleepWakeTime ? sleepSchedule.sleepWakeTime.slice(0, 5) : "—"}.
                </p>
            )}

            {isDiary && (
                <section className={styles.daysSection}>
                    <h2 className={styles.sectionTitle}>{insideOpenTask ? "Dias do período" : "Dia da semana"}</h2>
                    {insideOpenTask && (
                        <p className={styles.daysHint}>
                            Esqueceu algum dia? Escolha o dia na grade e preencha agora.
                        </p>
                    )}
                    <div
                        className={styles.days}
                        role="group"
                        aria-label={insideOpenTask ? "Dias do período" : "Dias da semana"}
                    >
                        {gridDays.map((dayIso) => {
                            // dia so com resposta anulada volta a ficar em branco
                            const isFilled = responses.some((response) => response.periodStart === dayIso
                                && !response.annulled);
                            return (
                                <button
                                    key={dayIso}
                                    type="button"
                                    className={dayIso === selectedDay ? styles.day + " " + styles.dayChosen : styles.day}
                                    aria-pressed={dayIso === selectedDay}
                                    onClick={() => changeDay(dayIso)}
                                >
                                    <span className={styles.dayName}>{formatWeekdayAndDate(dayIso)}</span>
                                    <span className={isFilled ? styles.dayMark + " " + styles.dayFilled : styles.dayMark}>
                                        {isFilled && <FiCheck aria-hidden="true"/>}
                                        {isFilled ? "preenchido" : "em branco"}
                                    </span>
                                </button>
                            );
                        })}
                    </div>
                </section>
            )}

            <form className={styles.form} onSubmit={save}>
                {!isDiary && (
                    <div className={styles.period}>
                        <div className={styles.dateField}>
                            <label htmlFor="periodStart">
                                {isPeriod ? "Início do período" : "Data da avaliação"}
                            </label>
                            <input
                                id="periodStart"
                                type="date"
                                max={today}
                                value={periodStart}
                                disabled={isReadOnly}
                                onChange={(event) => (isCorrecting
                                    ? changePeriodStart(event.target.value)
                                    : changeDay(event.target.value))}
                            />
                        </div>
                        {isPeriod && (
                            <div className={styles.dateField}>
                                <label htmlFor="periodEnd">Fim do período</label>
                                <input
                                    id="periodEnd"
                                    type="date"
                                    value={periodEnd}
                                    disabled={isReadOnly}
                                    onChange={(event) => changePeriodEnd(event.target.value)}
                                />
                            </div>
                        )}
                    </div>
                )}

                {isReadOnly && (
                    <p className="aviso">O prescritor já analisou esta resposta. Para corrigir, fale com ele.</p>
                )}

                {hasAnnulledResponse && (
                    <p className="aviso">
                        A resposta desta data foi anulada pelo prescritor e continua no histórico. Você pode responder
                        de novo.
                    </p>
                )}

                {currentResponse && currentResponse.result && (
                    <div className={styles.result}>
                        <span className={styles.resultLabel}>Resultado</span>
                        <strong>{currentResponse.result}</strong>
                    </div>
                )}

                {formError && <p className="aviso aviso--atencao" role="alert">{formError}</p>}
                {notice && <p className="aviso" role="status">{notice}</p>}

                {hasRequiredItems && !isReadOnly && (
                    <p className="aviso">
                        Responda todos os itens antes de salvar. Sem eles a escala pode ficar sem escore e continuar pendente.
                    </p>
                )}

                <ScaleForm
                    items={definition.items}
                    values={values}
                    onChange={changeAnswer}
                    disabled={isReadOnly || isSaving}
                    missingKeys={missingKeys}
                />

                {!isDiary && currentResponse && (
                    <p className={styles.periodNote}>
                        {isPeriod
                            ? "Período respondido: " + formatDate(currentResponse.periodStart) + " a "
                                + formatDate(currentResponse.periodEnd) + "."
                            : "Avaliação de " + formatDate(currentResponse.periodStart) + "."}
                        {isCorrecting && " Mudar a data corrige esta resposta, sem criar outra."}
                    </p>
                )}

                {/* perto do botao pq no topo o aviso some num form comprido */}
                {missingLabels.length > 0 && (
                    <p className="aviso aviso--atencao" role="alert">
                        Falta preencher: {missingLabels.join(", ")}.
                    </p>
                )}

                <FormActions>
                    <button type="button" className="button-secondary" onClick={() => navigate(-1)}>
                        Voltar
                    </button>
                    {!isReadOnly && (
                        <button type="submit" className="button" disabled={isSaving}>
                            {isSaving ? "Salvando..." : "Salvar respostas"}
                        </button>
                    )}
                </FormActions>
            </form>
        </section>
    );
}
