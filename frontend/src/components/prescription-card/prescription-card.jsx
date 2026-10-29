import AnnulmentNotice from "../annulment-notice/annulment-notice.jsx";
import {formatDate} from "../../utils/date-format.js";
import {compositionSummary, labelOf, prescriptionStatusLabel, SPECTRUM_OPTIONS} from "../../utils/prescription-labels.js";
import styles from "./prescription-card.module.css";

function badgeClassOf(prescription) {
    if (prescription.current) {
        return styles.badgeCurrent;
    }
    return prescription.annulled ? styles.badgeAnnulled : styles.badgeReplaced;
}

function DetailItem({term, children}) {
    return (
        <div>
            <dt>{term}</dt>
            <dd>{children}</dd>
        </div>
    );
}

function TextBlock({title, text}) {
    return (
        <div className={styles.block}>
            <h4>{title}</h4>
            <p>{text}</p>
        </div>
    );
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
                <DetailItem term="Composição">
                    {compositionSummary(prescription.components) || "Não informada"}
                </DetailItem>
                {prescription.spectrum && (
                    <DetailItem term="Espectro">{labelOf(SPECTRUM_OPTIONS, prescription.spectrum)}</DetailItem>
                )}
                {brandAndBatch && <DetailItem term="Marca e lote">{brandAndBatch}</DetailItem>}
                {prescription.volume && <DetailItem term="Volume">{prescription.volume} mL</DetailItem>}
                <DetailItem term="Posologia">{prescription.posology}</DetailItem>
                {prescription.administrationRoute && (
                    <DetailItem term="Via">{prescription.administrationRoute}</DetailItem>
                )}
                {prescription.treatmentDurationDays && (
                    <DetailItem term="Duração">{prescription.treatmentDurationDays} dias</DetailItem>
                )}
                {prescription.nextConsultationDate && (
                    <DetailItem term="Próxima consulta">{formatDate(prescription.nextConsultationDate)}</DetailItem>
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

            {prescription.instructions && <TextBlock title="Instruções de uso" text={prescription.instructions}/>}
            {prescription.precautions && (
                <TextBlock title="Precauções e contraindicações" text={prescription.precautions}/>
            )}
            {prescription.expectedEffects && <TextBlock title="Efeitos esperados" text={prescription.expectedEffects}/>}
            {prescription.observation && <TextBlock title="Observações" text={prescription.observation}/>}

            {prescription.annulled && <AnnulmentNotice record={prescription}/>}

            {actions && <div className={styles.actions}>{actions}</div>}
        </article>
    );
}
