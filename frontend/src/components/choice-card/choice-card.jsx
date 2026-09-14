import {FiCheck} from "react-icons/fi";
import styles from "./choice-card.module.css";

// cartao q se marca e desmarca inteiro, tipo escolher quais escalas enviar
// o checkbox de verdade continua ali embaixo pro teclado e pro leitor de tela
// children aparece do lado quando precisa de mais uma escolha, como a frequencia
export default function ChoiceCard({title, description, checked, disabled = false, onChange, children}) {
    let cardClass = styles.card;
    if (checked) {
        cardClass = cardClass + " " + styles.checked;
    }
    if (disabled) {
        cardClass = cardClass + " " + styles.disabled;
    }

    return (
        <div className={cardClass}>
            <label className={styles.choice}>
                <input
                    type="checkbox"
                    className={styles.input}
                    checked={checked}
                    disabled={disabled}
                    onChange={onChange}
                />
                <span className={styles.box} aria-hidden="true"><FiCheck/></span>
                <span className={styles.text}>
                    <strong className={styles.title}>{title}</strong>
                    {description && <span className={styles.description}>{description}</span>}
                </span>
            </label>
            {children && <div className={styles.extra}>{children}</div>}
        </div>
    );
}
