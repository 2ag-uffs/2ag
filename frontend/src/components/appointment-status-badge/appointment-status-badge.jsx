import {statusLabelOf} from "../../utils/appointment-labels.js";
import styles from "./appointment-status-badge.module.css";

// etiqueta com a situacao da consulta ou do pedido na agenda
export default function AppointmentStatusBadge({appointment}) {
    let badgeClass = styles.inactive;
    if (!appointment.annulled && appointment.status === "SOLICITADA") {
        badgeClass = styles.waiting;
    } else if (!appointment.annulled && (appointment.status === "AGENDADA" || appointment.status === "EM_ANDAMENTO")) {
        badgeClass = styles.scheduled;
    } else if (!appointment.annulled && appointment.status === "CONCLUIDA") {
        badgeClass = styles.done;
    }

    return <span className={badgeClass}>{statusLabelOf(appointment)}</span>;
}
