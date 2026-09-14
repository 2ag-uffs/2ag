import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
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

        apiService.get("/pacientes/" + patientId + "/escalas/central")
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
        return <p>Carregando avaliações...</p>;
    }

    return (
        <section className={styles.page}>
            <header>
                <h1>Minhas avaliações clínicas</h1>
                <p className={styles.subtitle}>
                    Acompanhe o que está esperando resposta e o resultado do que você já respondeu.
                </p>
            </header>

            <section>
                <h2 className={styles.sectionTitle}>Esperando resposta ({page.pending.length})</h2>
                {page.pending.length === 0 ? (
                    <p className={styles.empty}>Nenhuma avaliação esperando resposta agora.</p>
                ) : (
                    <ul className={styles.cards}>
                        {page.pending.map((task) => (
                            <li key={task.id} className={styles.card}>
                                <h3 className={styles.cardTitle}>{task.scaleName}</h3>
                                <p className={styles.deadline}>
                                    {task.late
                                        ? "O prazo era " + formatDate(task.periodEnd)
                                        : "Responda até " + formatDate(task.periodEnd)}
                                </p>
                                {task.totalDays > 1 && (
                                    <p className={styles.progress}>
                                        {task.answeredDays} de {task.totalDays} dias preenchidos
                                    </p>
                                )}
                                <button type="button" className="button" onClick={() => navigate(task.path)}>
                                    Preencher
                                </button>
                            </li>
                        ))}
                    </ul>
                )}
            </section>

            <section>
                <h2 className={styles.sectionTitle}>Respondidas ({page.history.length})</h2>
                {page.history.length === 0 ? (
                    <p className={styles.empty}>Você ainda não respondeu nenhuma avaliação.</p>
                ) : (
                    <ul className={styles.list}>
                        {page.history.map((response) => (
                            <li key={response.id} className={styles.row}>
                                <div>
                                    <h3 className={styles.cardTitle}>{response.scaleName}</h3>
                                    <p className={styles.period}>{periodTextOf(response)}</p>
                                    <p className={styles.result}>{response.result}</p>
                                    {response.annulled && <p className={styles.annulled}>Anulada pelo prescritor</p>}
                                    {response.reviewed && !response.annulled && (
                                        <p className={styles.period}>O prescritor já analisou</p>
                                    )}
                                </div>
                                {/* o q ainda da pra corrigir abre o formulario e o resto so a leitura */}
                                {/* o MEEM cai sempre na leitura pq quem aplica eh o prescritor */}
                                <button
                                    type="button"
                                    className="button-secondary"
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
            </section>
        </section>
    );
}

// o diario fala de um dia e os acompanhamentos de um periodo
function periodTextOf(response) {
    return response.periodStart === response.periodEnd
        ? formatDate(response.periodStart)
        : formatDate(response.periodStart) + " a " + formatDate(response.periodEnd);
}
