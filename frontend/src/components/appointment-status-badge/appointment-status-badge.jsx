import {statusLabelOf} from "../../utils/appointment-labels.js";

// a variante da etiqueta (styles/etiqueta.css)
function badgeClassOf(appointment) {
    if (appointment.annulled) {
        return "etiqueta etiqueta--neutra";
    }
    // pedido esperando resposta ganha borda pra chamar atencao sem parecer erro
    if (appointment.status === "SOLICITADA") {
        return "etiqueta etiqueta--atencao";
    }
    if (appointment.status === "AGENDADA" || appointment.status === "EM_ANDAMENTO") {
        return "etiqueta etiqueta--ok";
    }
    return "etiqueta etiqueta--neutra";
}

// etiqueta com a situacao da consulta ou do pedido na agenda
export default function AppointmentStatusBadge({appointment}) {
    return <span className={badgeClassOf(appointment)}>{statusLabelOf(appointment)}</span>;
}
