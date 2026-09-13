import styles from "./form.module.css";

// campo com rotulo dica e mensagem de erro
// as outras props vao direto pro input
export default function TextField({label, name, error, hint, ...inputProps}) {
    const errorId = name + "-error";

    return (
        <div className={styles.field}>
            <label htmlFor={name} className={styles.label}>{label}</label>
            <input
                id={name}
                name={name}
                aria-invalid={error ? "true" : undefined}
                aria-describedby={error ? errorId : undefined}
                {...inputProps}
            />
            {hint && !error && <span className={styles.hint}>{hint}</span>}
            {error && <span id={errorId} className={styles.error}>{error}</span>}
        </div>
    );
}
