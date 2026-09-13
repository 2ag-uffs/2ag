import styles from "./form.module.css";

// campo de texto longo com rotulo dica e mensagem de erro
// as outras props vao direto pro textarea
export default function TextAreaField({label, name, error, hint, rows = 4, ...textAreaProps}) {
    const errorId = name + "-error";

    return (
        <div className={styles.field}>
            <label htmlFor={name} className={styles.label}>{label}</label>
            <textarea
                id={name}
                name={name}
                rows={rows}
                className={styles.textArea}
                aria-invalid={error ? "true" : undefined}
                aria-describedby={error ? errorId : undefined}
                {...textAreaProps}
            />
            {hint && !error && <span className={styles.hint}>{hint}</span>}
            {error && <span id={errorId} className={styles.error}>{error}</span>}
        </div>
    );
}
