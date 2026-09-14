import {formatDate} from "../../utils/date-format.js";
import styles from "./scale-summary.module.css";

// resumo das escalas de um paciente com o q espera resposta e o q ja
// foi respondido (RF12 e RF13)
//
// quem usa escolhe o titulo das pendentes e passa as acoes q aquele
// perfil pode fazer com a resposta
export default function ScaleSummary({scalesPage, pendingTitle, pendingAction, onOpenResponse, onReview, onAnnul}) {
    const pending = scalesPage.pending;
    const history = scalesPage.history;

    return (
        <div className={styles.boxes}>
            <div className={styles.box}>
                <h3>{pendingTitle}</h3>
                {pending.length === 0 ? (
                    <p className={styles.empty}>Nenhuma escala esperando resposta.</p>
                ) : (
                    <ul className={styles.scaleList}>
                        {pending.map((task) => (
                            <li key={task.id}>
                                <span>{task.scaleName}</span>
                                <span className={styles.date}>
                                    {task.late ? "prazo em " + formatDate(task.periodEnd) + ", atrasada"
                                        : "até " + formatDate(task.periodEnd)}
                                    {task.totalDays > 1 ? " · " + task.answeredDays + " de " + task.totalDays + " dias" : ""}
                                </span>
                            </li>
                        ))}
                    </ul>
                )}
                {pending.length > 0 && pendingAction}
            </div>

            <div className={styles.box}>
                <h3>Respondidas</h3>
                {history.length === 0 ? (
                    <p className={styles.empty}>Nenhuma escala respondida ainda.</p>
                ) : (
                    <ul className={styles.scaleList}>
                        {history.map((response) => (
                            <li key={response.id} className={styles.response}>
                                <div className={styles.responseText}>
                                    <span>{response.scaleName}</span>
                                    {/* data resultado e situacao numa linha so embaixo do nome */}
                                    <span className={styles.responseMeta}>
                                        <span className={styles.date}>{periodTextOf(response)}</span>
                                        <span className={styles.result}>{response.result}</span>
                                        {response.annulled && <span className={styles.annulled}>anulada</span>}
                                        {response.reviewed && !response.annulled && (
                                            <span className={styles.date}>analisada</span>
                                        )}
                                    </span>
                                </div>
                                <div className={styles.actions}>
                                    {onOpenResponse && (
                                        <button type="button" className="button-tertiary"
                                                onClick={() => onOpenResponse(response)}>
                                            Ver respostas
                                        </button>
                                    )}
                                    {onReview && !response.reviewed && !response.annulled && (
                                        <button type="button" className="button-tertiary"
                                                onClick={() => onReview(response)}>
                                            Marcar como analisada
                                        </button>
                                    )}
                                    {onAnnul && !response.annulled && (
                                        <button type="button" className="button-tertiary"
                                                onClick={() => onAnnul(response)}>
                                            Anular
                                        </button>
                                    )}
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
            </div>
        </div>
    );
}

// o diario fala de um dia e os acompanhamentos de um periodo
function periodTextOf(response) {
    return response.periodStart === response.periodEnd
        ? formatDate(response.periodStart)
        : formatDate(response.periodStart) + " a " + formatDate(response.periodEnd);
}
