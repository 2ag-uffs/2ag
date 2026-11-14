import styles from "./urgency-notice.module.css";

export const URGENCY_NOTICE = "Isto não é atendimento de urgência. Se algo piorou, fale com a clínica ou procure "
    + "um serviço de saúde.";

// o q o paciente escreve n eh lido na hora, entao junto de todo texto livre dele fica dito q o sistema n eh
// canal de urgencia (decisao de 14/11/2026, revisao das regras R1)
// o id deixa o campo ligar o aviso pelo aria-describedby, e o leitor de tela le junto com o campo
export default function UrgencyNotice({id}) {
    return <p id={id} className={styles.notice}>{URGENCY_NOTICE}</p>;
}
