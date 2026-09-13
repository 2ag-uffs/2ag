import {formatDate} from "../../utils/date-format.js";
import styles from "./annulment-notice.module.css";

// faixa q mostra quando quem e por q o registro foi anulado
// consulta prescricao e anamnese sao palavras femininas entao o texto serve pras tres
export default function AnnulmentNotice({record}) {
    return (
        <p className={styles.notice}>
            Anulada em {formatDate(record.annulledAt)} por {record.annulledByName}. Motivo: {record.annulmentReason}
        </p>
    );
}
