import styles from "./form.module.css";

// campo de texto longo com rotulo dica e mensagem de erro
// o noticeId liga um aviso de fora do campo, e o leitor de tela le o erro e o aviso junto com ele
// as outras props vao direto pro textarea
export default function TextAreaField({label, name, error, hint, noticeId, rows = 4, ...textAreaProps}) {
    const errorId = name + "-error";
    const describedBy = [error ? errorId : null, noticeId || null]
        .filter((id) => id !== null)
        .join(" ");

    return (
        <div className={styles.field}>
            <label htmlFor={name} className={styles.label}>{label}</label>
            <textarea
                id={name}
                name={name}
                rows={rows}
                className={styles.textArea}
                aria-invalid={error ? "true" : undefined}
                aria-describedby={describedBy === "" ? undefined : describedBy}
                {...textAreaProps}
            />
            {hint && !error && <span className={styles.hint}>{hint}</span>}
            {error && <span id={errorId} className={styles.error}>{error}</span>}
        </div>
    );
}
