import {statusLabelOf} from "../../utils/appointment-labels.js";
import styles from "./appointment-status-badge.module.css";

function badgeClassOf(appointment) {
    if (appointment.annulled) {
        return styles.inactive;
    }
    if (appointment.status === "SOLICITADA") {
        return styles.waiting;
    }
    if (appointment.status === "AGENDADA" || appointment.status === "EM_ANDAMENTO") {
        return styles.scheduled;
    }
    if (appointment.status === "CONCLUIDA") {
        return styles.done;
    }
    return styles.inactive;
}

// etiqueta com a situacao da consulta ou do pedido na agenda
export default function AppointmentStatusBadge({appointment}) {
    return <span className={badgeClassOf(appointment)}>{statusLabelOf(appointment)}</span>;
}
