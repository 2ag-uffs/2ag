import {useEffect, useRef, useState} from "react";
import {FiEye, FiEyeOff} from "react-icons/fi";
import styles from "./form.module.css";

// campo de senha com um botao pra ver o q foi digitado
// ajuda muito no celular onde eh facil errar uma letra
// com a senha a mostra o campo vira texto comum, entao o corretor ortografico e a maiuscula
// automatica do teclado ficam desligados: o corretor pode mandar o q le pra um servico de fora
export default function PasswordField({label, name, error, ...inputProps}) {
    const [isVisible, setIsVisible] = useState(false);
    const inputRef = useRef(null);
    const errorId = name + "-error";
    const visibilityLabel = isVisible ? "Esconder senha" : "Mostrar senha";

    // formulario enviado com a senha a mostra: o campo volta a esconder, senao o navegador pode
    // guardar a senha como texto comum, entre as sugestoes de preenchimento
    useEffect(() => {
        const form = inputRef.current.form;
        if (!form) {
            return undefined;
        }
        const hidePassword = () => setIsVisible(false);
        form.addEventListener("submit", hidePassword);
        return () => form.removeEventListener("submit", hidePassword);
    }, []);

    return (
        <div className={styles.field}>
            <label htmlFor={name} className={styles.label}>{label}</label>
            <div className={styles.passwordBox}>
                <input
                    id={name}
                    name={name}
                    type={isVisible ? "text" : "password"}
                    spellCheck={false}
                    autoCapitalize="off"
                    aria-invalid={error ? "true" : undefined}
                    aria-describedby={error ? errorId : undefined}
                    {...inputProps}
                    ref={inputRef}
                />
                <button
                    type="button"
                    className={styles.visibilityButton}
                    onClick={() => setIsVisible(!isVisible)}
                    aria-label={visibilityLabel}
                    title={visibilityLabel}
                >
                    {isVisible ? <FiEyeOff/> : <FiEye/>}
                </button>
            </div>
            {error && <span id={errorId} className={styles.error}>{error}</span>}
        </div>
    );
}
