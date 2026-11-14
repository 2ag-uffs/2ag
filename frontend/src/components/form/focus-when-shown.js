import {useEffect, useRef} from "react";

// o formulario some dps de salvar e o foco do botao ia pro body, sumindo da tela de quem usa o teclado (CT-39)
// entao o aviso de salvo q entra no lugar recebe o foco, e o leitor de tela le o q aconteceu
// o aviso precisa do tabIndex={-1} pra receber o foco
export function useFocusWhenShown(isShown) {
    const elementRef = useRef(null);

    useEffect(() => {
        if (isShown && elementRef.current) {
            elementRef.current.focus();
        }
    }, [isShown]);

    return elementRef;
}
