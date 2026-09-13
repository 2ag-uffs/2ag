import styles from "./form.module.css";

// lista de opcoes com rotulo e mensagem de erro
// as outras props vao direto pro select
export default function SelectField({label, name, error, options, placeholder, ...selectProps}) {
    const errorId = name + "-error";

    return (
        <div className={styles.field}>
            <label htmlFor={name} className={styles.label}>{label}</label>
            <select
                id={name}
                name={name}
                className={styles.select}
                aria-invalid={error ? "true" : undefined}
                aria-describedby={error ? errorId : undefined}
                {...selectProps}
            >
                {placeholder && <option value="">{placeholder}</option>}
                {options.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                ))}
            </select>
            {error && <span id={errorId} className={styles.error}>{error}</span>}
        </div>
    );
}
