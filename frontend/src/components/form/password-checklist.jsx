import {FiCheckCircle, FiCircle} from "react-icons/fi";
import {PASSWORD_RULES} from "./password-rules.js";
import styles from "./form.module.css";

// lista as regras da senha e marca as q ja foram cumpridas
export default function PasswordChecklist({password}) {
    return (
        <ul className={styles.checklist} aria-label="Regras da senha">
            {PASSWORD_RULES.map((rule) => {
                const isMet = rule.isMet(password);
                return (
                    <li key={rule.id} className={isMet ? styles.checklistItemMet : styles.checklistItem}>
                        {isMet ? <FiCheckCircle aria-hidden="true"/> : <FiCircle aria-hidden="true"/>}
                        <span>{rule.label}</span>
                        <span className={styles.visuallyHidden}>{isMet ? "cumprida" : "pendente"}</span>
                    </li>
                );
            })}
        </ul>
    );
}
