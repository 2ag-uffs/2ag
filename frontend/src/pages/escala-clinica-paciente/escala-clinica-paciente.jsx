import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {FiCheckCircle} from "react-icons/fi";
import Card from "../../components/card/card.jsx";
import EmptyState from "../../components/empty-state/empty-state.jsx";
import PageHeader from "../../components/page-header/page-header.jsx";
import SkeletonPage from "../../components/skeleton/skeleton.jsx";
import {apiService, ApiError} from "../../services/api.js";
import {formatDate} from "../../utils/date-format.js";
import styles from "./escala-clinica-paciente.module.css";

// central de escalas do paciente (RF08)
//
// mostra o q esta esperando resposta, com o prazo de cada tarefa, e o
// historico com o resultado real de cada escala respondida
export default function CentralEscalas() {
    const {patientId} = useParams();
    const navigate = useNavigate();

    const [page, setPage] = useState(null);
    const [loadError, setLoadError] = useState(null);

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
                    setLoadError(requestError instanceof ApiError
                        ? requestError.message
                        : "Não foi possível carregar suas avaliações. Confira sua internet e tente de novo.");
                }
            });

        return () => {
            isCurrentRequest = false;
        };
    }, [patientId]);

    if (loadError) {
        return <p className="aviso aviso--atencao" role="alert">{loadError}</p>;
    }

    if (!page) {
        return <SkeletonPage cards={2}/>;
    }

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
                        {page.pending.map((task) => {
                            // quanto do periodo ja foi preenchido, nos diarios de varios dias
                            const filledPercent = task.totalDays > 1
                                ? Math.round((task.answeredDays / task.totalDays) * 100)
                                : 0;
                            return (
                                <li key={task.id} className={task.late ? styles.card + " " + styles.cardLate : styles.card}>
                                    <h3 className={styles.cardTitle}>{task.scaleName}</h3>
                                    <p className={task.late ? styles.deadlineLate : styles.deadline}>
                                        {task.late
                                            ? "O prazo era " + formatDate(task.periodEnd)
                                            : "Responda até " + formatDate(task.periodEnd)}
                                    </p>
                                    {task.totalDays > 1 && (
                                        <div className={styles.progress}>
                                            <div
                                                className={styles.progressBar}
                                                role="progressbar"
                                                aria-valuemin={0}
                                                aria-valuemax={task.totalDays}
                                                aria-valuenow={task.answeredDays}
                                            >
                                                <span className={styles.progressFill} style={{width: filledPercent + "%"}}/>
                                            </div>
                                            <span className={styles.progressText}>
                                                {task.answeredDays} de {task.totalDays} dias preenchidos
                                            </span>
                                        </div>
                                    )}
                                    <button type="button" className="button button-small" onClick={() => navigate(task.path)}>
                                        Responder
                                    </button>
                                </li>
                            );
                        })}
                    </ul>
                )}
            </section>

            <Card title="Respondidas" count={page.history.length}>
                {page.history.length === 0 ? (
                    <p className={styles.period}>Você ainda não respondeu nenhuma avaliação.</p>
                ) : (
                    <ul className={styles.list}>
                        {page.history.map((response) => (
                            <li key={response.id} className={styles.row}>
                                <div className={styles.rowText}>
                                    <h3 className={styles.cardTitle}>{response.scaleName}</h3>
                                    <p className={styles.period}>{periodTextOf(response)}</p>
                                    <p className={styles.result}>{response.result}</p>
                                    {response.annulled && <span className={styles.tagAnnulled}>Anulada pelo prescritor</span>}
                                    {response.reviewed && !response.annulled && (
                                        <span className={styles.tagReviewed}>O prescritor já analisou</span>
                                    )}
                                </div>
                                {/* o q ainda da pra corrigir abre o formulario e o resto so a leitura */}
                                {/* o MEEM cai sempre na leitura pq quem aplica eh o prescritor */}
                                <button
                                    type="button"
                                    className="button-secondary button-small"
                                    onClick={() => navigate(response.editableByPatient
                                        ? "/escalas/" + response.slug + "?data=" + response.periodStart
                                        : "/escalas/resposta/" + response.id)}
                                >
                                    {response.editableByPatient ? "Ver e corrigir" : "Ver respostas"}
                                </button>
                            </li>
                        ))}
                    </ul>
                )}
            </Card>
        </section>
    );
}

// o diario fala de um dia e os acompanhamentos de um periodo
function periodTextOf(response) {
    return response.periodStart === response.periodEnd
        ? formatDate(response.periodStart)
        : formatDate(response.periodStart) + " a " + formatDate(response.periodEnd);
}
