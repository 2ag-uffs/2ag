import {useId} from "react";
import styles from "./form-section.module.css";

// bloco de um formulario comprido, tipo "Queixa e exame" no registro da consulta
// eh um fieldset de verdade entao o disabled trava todos os campos de dentro de uma vez
// a descricao fica ligada ao bloco pro leitor de tela ler junto com o titulo
export default function FormSection({title, description, disabled = false, children}) {
    const descriptionId = useId();

    return (
        <fieldset
            className={styles.section}
            disabled={disabled}
            aria-describedby={description ? descriptionId : undefined}
        >
            <legend className={styles.legend}>{title}</legend>
            {description && <p id={descriptionId} className={styles.description}>{description}</p>}
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
// o data-bottom-bar faz a pagina subir o campo com foco q fica atras deles (utils/focus-above-bars.js)
export function FormActions({children}) {
    return <div className={styles.actions} data-bottom-bar="">{children}</div>;
}
