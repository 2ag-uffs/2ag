import styles from "./form.module.css";

// campo escondido q pessoa n ve e robo preenche
// o nome foge de site e url pra o navegador n preencher sozinho
export default function TrapField({value, onChange}) {
    return (
        <input
            className={styles.trap}
            type="text"
            name="nao-preencha"
            value={value}
            onChange={(event) => onChange(event.target.value)}
            tabIndex={-1}
            autoComplete="off"
            aria-hidden="true"
        />
    );
}
