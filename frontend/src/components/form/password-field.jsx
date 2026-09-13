import {useState} from "react";
import {FiEye, FiEyeOff} from "react-icons/fi";
import styles from "./form.module.css";

// campo de senha com um botao pra ver o q foi digitado
// ajuda muito no celular onde eh facil errar uma letra
export default function PasswordField({label, name, error, ...inputProps}) {
    const [isVisible, setIsVisible] = useState(false);
    const errorId = name + "-error";

    return (
        <div className={styles.field}>
            <label htmlFor={name} className={styles.label}>{label}</label>
            <div className={styles.passwordBox}>
                <input
                    id={name}
                    name={name}
                    type={isVisible ? "text" : "password"}
                    aria-invalid={error ? "true" : undefined}
                    aria-describedby={error ? errorId : undefined}
                    {...inputProps}
                />
                <button
                    type="button"
                    className={styles.visibilityButton}
                    onClick={() => setIsVisible(!isVisible)}
                    aria-label={isVisible ? "Esconder senha" : "Mostrar senha"}
                    title={isVisible ? "Esconder senha" : "Mostrar senha"}
                >
                    {isVisible ? <FiEyeOff/> : <FiEye/>}
                </button>
            </div>
            {error && <span id={errorId} className={styles.error}>{error}</span>}
        </div>
    );
}
