import {compositionSummary, labelOf, prescriptionStatusLabel, SPECTRUM_OPTIONS} from "../../utils/prescription-labels.js";
import styles from "./prescription-card.module.css";

// data da api no formato brasileiro sem deixar a data sem hora virar o dia anterior
function formatDate(isoDate) {
    if (!isoDate) {
        return "";
    }
    const dateOnly = isoDate.length === 10 ? isoDate + "T00:00:00" : isoDate;
    return new Date(dateOnly).toLocaleDateString("pt-BR");
}

function badgeClassOf(prescription) {
    if (prescription.current) {
        return styles.badgeCurrent;
    }
    return prescription.annulled ? styles.badgeAnnulled : styles.badgeReplaced;
}

// cartao de uma prescricao usado pelo paciente e pelo prescritor
// o actions deixa quem usa colocar botoes como o de anular
export default function PrescriptionCard({prescription, actions}) {
    const brandAndBatch = [prescription.brand, prescription.batch ? "lote " + prescription.batch : null]
        .filter(Boolean)
        .join(" · ");

    return (
        <article className={prescription.current ? styles.card + " " + styles.cardCurrent : styles.card}>
            <header className={styles.header}>
                <div>
                    <h3>{prescription.productDescription}</h3>
                    <p className={styles.meta}>
                        Emitida em {formatDate(prescription.appointmentDateTime)}
                        {prescription.prescriberName ? " por " + prescription.prescriberName : ""}
                    </p>
                </div>
                <span className={badgeClassOf(prescription)}>{prescriptionStatusLabel(prescription)}</span>
            </header>

            <dl className={styles.details}>
                <div>
                    <dt>Composição</dt>
                    <dd>{compositionSummary(prescription.components) || "Não informada"}</dd>
                </div>
                {prescription.spectrum && (
                    <div>
                        <dt>Espectro</dt>
                        <dd>{labelOf(SPECTRUM_OPTIONS, prescription.spectrum)}</dd>
                    </div>
                )}
                {brandAndBatch && (
                    <div>
                        <dt>Marca e lote</dt>
                        <dd>{brandAndBatch}</dd>
                    </div>
                )}
                {prescription.volume && (
                    <div>
                        <dt>Volume</dt>
                        <dd>{prescription.volume} mL</dd>
                    </div>
                )}
                <div>
                    <dt>Posologia</dt>
                    <dd>{prescription.posology}</dd>
                </div>
                {prescription.administrationRoute && (
                    <div>
                        <dt>Via</dt>
                        <dd>{prescription.administrationRoute}</dd>
                    </div>
                )}
                {prescription.treatmentDurationDays && (
                    <div>
                        <dt>Duração</dt>
                        <dd>{prescription.treatmentDurationDays} dias</dd>
                    </div>
                )}
                {prescription.nextConsultationDate && (
                    <div>
                        <dt>Próxima consulta</dt>
                        <dd>{formatDate(prescription.nextConsultationDate)}</dd>
                    </div>
                )}
            </dl>

            {prescription.escalationSteps.length > 0 && (
                <div className={styles.block}>
                    <h4>Escalonamento de dose</h4>
                    <ol className={styles.steps}>
                        {prescription.escalationSteps.map((step) => (
                            <li key={step.week}>
                                <strong>Semana {step.week}:</strong> {step.dosage}{step.note ? " · " + step.note : ""}
                            </li>
                        ))}
                    </ol>
                </div>
            )}

            {prescription.instructions && (
                <div className={styles.block}>
                    <h4>Instruções de uso</h4>
                    <p>{prescription.instructions}</p>
                </div>
            )}
            {prescription.precautions && (
                <div className={styles.block}>
                    <h4>Precauções e contraindicações</h4>
                    <p>{prescription.precautions}</p>
                </div>
            )}
            {prescription.expectedEffects && (
                <div className={styles.block}>
                    <h4>Efeitos esperados</h4>
                    <p>{prescription.expectedEffects}</p>
                </div>
            )}

            {prescription.annulled && (
                <p className={styles.annulment}>
                    Anulada em {formatDate(prescription.annulledAt)} por {prescription.annulledByName}.
                    Motivo: {prescription.annulmentReason}
                </p>
            )}

            {actions && <div className={styles.actions}>{actions}</div>}
        </article>
    );
}
