import {useEffect, useRef, useState} from "react";

// leva o foco ate o primeiro campo com erro dentro de quem foi passado e diz se achou algum
// o erro fica ligado ao campo pelo aria-describedby, entao o leitor de tela le o campo e o motivo juntos
export function focusFirstInvalidField(container) {
    const firstInvalidField = container.querySelector("[aria-invalid='true']");
    if (!firstInvalidField) {
        return false;
    }
    firstInvalidField.focus();
    return true;
}

// envio recusado com erro de campo: o foco sai do botao e vai pro primeiro campo marcado (decisao de 13/11/2026)
// o formulario recebe o formRef e chama focusFirstError() junto com os erros
// eh um efeito pq o campo so fica marcado depois q a tela desenha de novo
export function useFocusFirstError() {
    const formRef = useRef(null);
    const [refusedCount, setRefusedCount] = useState(0);

    useEffect(() => {
        if (refusedCount > 0 && formRef.current) {
            focusFirstInvalidField(formRef.current);
        }
    }, [refusedCount]);

    const focusFirstError = () => setRefusedCount((count) => count + 1);
    return [formRef, focusFirstError];
}
