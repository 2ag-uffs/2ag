import {useEffect, useId, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {FiCheckCircle, FiChevronDown, FiChevronUp, FiTrendingUp} from "react-icons/fi";
import Card from "../../components/card/card.jsx";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, errorMessageOf} from "../../services/api.js";
import {formatDate, formatPeriod} from "../../utils/date-format.js";
import styles from "./escala-clinica-paciente.module.css";

// 90 dias pra resposta de mais de um mes ainda aparecer no grafico
const PROGRESS_PERIOD = "DIAS_90";

function PendingTaskCard({task, onAnswer}) {
    // quanto do periodo ja foi preenchido, nos diarios de varios dias
    const filledPercent = task.daily
        ? Math.round((task.answeredDays / task.totalDays) * 100)
        : 0;
    // a barra leva o nome da escala e o texto dos dias, senao o leitor de tela le so a porcentagem
    const titleId = useId();
    const progressTextId = useId();
    return (
        <li className={task.late ? styles.card + " " + styles.cardLate : styles.card}>
            <h3 id={titleId} className={styles.cardTitle}>{task.scaleName}</h3>
            <p className={task.late ? styles.deadlineLate : styles.deadline}>
                {task.late
                    ? "O prazo era " + formatDate(task.periodEnd)
                    : "Responda até " + formatDate(task.periodEnd)}
            </p>
            {task.daily && (
                <div className={styles.progress}>
                    <div
                        className={styles.progressBar}
                        role="progressbar"
                        aria-valuemin={0}
                        aria-valuemax={task.totalDays}
                        aria-valuenow={task.answeredDays}
                        aria-labelledby={titleId + " " + progressTextId}
                    >
                        <span className={styles.progressFill} style={{width: filledPercent + "%"}}/>
                    </div>
                    <span id={progressTextId} className={styles.progressText}>
                        {task.answeredDays} de {task.totalDays} dias preenchidos
                    </span>
                </div>
            )}
            <button type="button" className="button button-small" onClick={onAnswer}>
                Responder
            </button>
        </li>
    );
}

// o q ainda da pra corrigir abre o formulario e o resto so a leitura
function answersPathOf(response) {
    if (response.editableByPatient) {
        return "/escalas/" + response.slug + "?data=" + response.periodStart;
    }
    return "/escalas/resposta/" + response.id;
}

// as respondidas juntas por escala, na ordem em q a api manda (da mais recente pra mais antiga)
// a q aparece na linha eh a mais recente q vale: a anulada n conta como resultado e fica com as outras,
// a n ser q a escala so tenha anuladas
function groupByScale(history) {
    const groups = [];
    history.forEach((response) => {
        const group = groups.find((currentGroup) => currentGroup.scaleType === response.scaleType);
        if (group) {
            group.responses.push(response);
        } else {
            groups.push({scaleType: response.scaleType, responses: [response]});
        }
    });
    return groups.map((group) => {
        const latest = group.responses.find((response) => !response.annulled) || group.responses[0];
        return {
            scaleType: group.scaleType,
            latest: latest,
            others: group.responses.filter((response) => response !== latest),
        };
    });
}

function ResponseTags({response}) {
    return (
        <>
            {response.annulled && <span className={styles.tagAnnulled}>Anulada pelo prescritor</span>}
            {response.reviewed && !response.annulled && (
                <span className={styles.tagReviewed}>O prescritor já analisou</span>
            )}
        </>
    );
}

// uma linha por escala: o resultado mais recente, um Ver progresso so e as outras respostas guardadas
// dentro, q abrem qnd a pessoa pede (decisao da dona do projeto, novembro de 2026). antes a tela mostrava
// todas as respostas do tratamento de uma vez, cada uma com o seu Ver progresso
function ScaleRow({group, progressAttribute, onOpenAnswers, onOpenProgress}) {
    const [isOpen, setIsOpen] = useState(false);
    const othersId = useId();
    const {latest, others} = group;
    const hasOneOther = others.length === 1;
    const openText = hasOneOther ? "Ver a outra resposta" : "Ver as outras " + others.length + " respostas";
    const closeText = hasOneOther ? "Esconder a outra resposta" : "Esconder as outras respostas";

    return (
        <li className={styles.row}>
            <div className={styles.rowHead}>
                <div className={styles.rowText}>
                    <h3 className={styles.cardTitle}>{latest.scaleName}</h3>
                    <p className={styles.period}>{formatPeriod(latest.periodStart, latest.periodEnd)}</p>
                    <p className={styles.result}>{latest.result}</p>
                    <ResponseTags response={latest}/>
                </div>
                <div className={styles.rowActions}>
                    <button
                        type="button"
                        className="button-secondary button-small"
                        onClick={() => onOpenAnswers(latest)}
                    >
                        {latest.editableByPatient ? "Ver e corrigir" : "Ver respostas"}
                    </button>
                    {progressAttribute && (
                        <button
                            type="button"
                            className="button-secondary button-small"
                            onClick={() => onOpenProgress(progressAttribute)}
                        >
                            <FiTrendingUp aria-hidden="true"/>
                            Ver progresso
                        </button>
                    )}
                </div>
            </div>

            {others.length > 0 && (
                <button
                    type="button"
                    className={"button-tertiary button-small " + styles.othersToggle}
                    aria-expanded={isOpen}
                    aria-controls={isOpen ? othersId : undefined}
                    onClick={() => setIsOpen(!isOpen)}
                >
                    {isOpen ? <FiChevronUp aria-hidden="true"/> : <FiChevronDown aria-hidden="true"/>}
                    {isOpen ? closeText : openText}
                </button>
            )}
            {isOpen && (
                <ul id={othersId} className={styles.others} aria-label={"Outras respostas de " + latest.scaleName}>
                    {others.map((response) => {
                        const periodId = othersId + "-" + response.id;
                        return (
                            <li key={response.id} className={styles.other}>
                                <div className={styles.rowText}>
                                    <p id={periodId} className={styles.otherPeriod}>
                                        {formatPeriod(response.periodStart, response.periodEnd)}
                                    </p>
                                    <p className={styles.otherResult}>{response.result}</p>
                                    <ResponseTags response={response}/>
                                </div>
                                {/* o leitor de tela le a data junto, senao sao varios botoes iguais */}
                                <button
                                    type="button"
                                    className="button-secondary button-small"
                                    aria-describedby={periodId}
                                    onClick={() => onOpenAnswers(response)}
                                >
                                    {response.editableByPatient ? "Ver e corrigir" : "Ver respostas"}
                                </button>
                            </li>
                        );
                    })}
                </ul>
            )}
        </li>
    );
}

// central de escalas do paciente (RF08)
//
// mostra o q esta esperando resposta, com o prazo de cada tarefa, e as
// respondidas, uma linha por escala com o resultado mais recente
export default function CentralEscalas() {
    const {patientId} = useParams();
    const navigate = useNavigate();

    const [page, setPage] = useState(null);
    const [loadError, setLoadError] = useState(null);
    // o q da pra acompanhar no grafico. sem ele a linha so perde o botao de progresso
    const [attributes, setAttributes] = useState([]);

    useEffect(() => {
        let isCurrentRequest = true;

        apiService.get("/patients/" + patientId + "/scales/overview")
            .then((loadedPage) => {
                if (isCurrentRequest) {
                    setPage(loadedPage);
                    setLoadError(null);
                }
            })
            .catch((requestError) => {
                if (isCurrentRequest) {
                    setLoadError(errorMessageOf(requestError,
                        "Não foi possível carregar suas avaliações. Confira sua internet e tente de novo."));
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [patientId]);

    useEffect(() => {
        apiService.get("/progress/attributes")
            .then(setAttributes)
            .catch(() => setAttributes([]));
    }, []);

    // o progresso abre no primeiro atributo da escala, igual qnd troca de escala la
    const firstAttributeOf = (scaleType) => attributes.find((attribute) => attribute.scaleType === scaleType);

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!page) {
        return <SkeletonPage cards={2}/>;
    }

    const answeredScales = groupByScale(page.history);

    return (
        <section className={styles.page}>
            <PageHeader
                title="Minhas avaliações clínicas"
                subtitle="Acompanhe o que está esperando resposta e o resultado do que você já respondeu."
            />

            <section className={styles.section}>
                <h2 className={styles.sectionTitle}>
                    Esperando resposta
                    <span className={styles.count}>{page.pending.length}</span>
                </h2>
                {page.pending.length === 0 ? (
                    <EmptyState
                        icon={FiCheckCircle}
                        message="Nenhuma avaliação esperando resposta agora. Você está em dia."
                        isCompact={true}
                    />
                ) : (
                    <ul className={styles.cards}>
                        {/* o link da api ja vem com a data da tarefa reaberta pela anulacao, senao a resposta de
                            hoje fechava a rodada nova da mesma escala */}
                        {page.pending.map((task) => (
                            <PendingTaskCard key={task.id} task={task} onAnswer={() => navigate(task.answerPath)}/>
                        ))}
                    </ul>
                )}
            </section>

            <Card title="Respondidas" count={answeredScales.length}>
                {answeredScales.length === 0 ? (
                    <p className={styles.period}>Você ainda não respondeu nenhuma avaliação.</p>
                ) : (
                    <ul className={styles.list}>
                        {answeredScales.map((group) => (
                            <ScaleRow
                                key={group.scaleType}
                                group={group}
                                progressAttribute={firstAttributeOf(group.scaleType)}
                                onOpenAnswers={(response) => navigate(answersPathOf(response))}
                                onOpenProgress={(attribute) => navigate("/progresso", {
                                    state: {attribute: attribute.name, period: PROGRESS_PERIOD},
                                })}
                            />
                        ))}
                    </ul>
                )}
            </Card>
        </section>
    );
}
