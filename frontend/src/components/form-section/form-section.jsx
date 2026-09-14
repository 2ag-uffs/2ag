import styles from "./form-section.module.css";

// bloco de um formulario comprido, tipo "Queixa e exame" no registro da consulta
// eh um fieldset de verdade entao o disabled trava todos os campos de dentro de uma vez
export default function FormSection({title, description, disabled = false, children}) {
    return (
        <fieldset className={styles.section} disabled={disabled}>
            <legend className={styles.legend}>{title}</legend>
            {description && <p className={styles.description}>{description}</p>}
            {children}
        </fieldset>
    );
}

// campos lado a lado q descem um embaixo do outro quando falta largura
export function FieldRow({children}) {
    return <div className={styles.row}>{children}</div>;
}

// botoes do fim do formulario
// ficam presos no pe da tela, entao salvar esta sempre a mao mesmo num formulario longo
export function FormActions({children}) {
    return <div className={styles.actions}>{children}</div>;
}
