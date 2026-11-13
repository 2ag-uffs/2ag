import {useEffect} from "react";
import {getLoggedUser} from "../services/api.js";

// recarregar, fechar a aba ou sair do app pelo navegador n passam pelo useBlocker do router
// entao enquanto tiver algo digitado e n salvo o proprio navegador pergunta antes
// sem ninguem logado a sessao ja acabou e a pagina vai pro login sem perguntar, igual no blocker
export function useLeaveWarning(hasUnsaved) {
    useEffect(() => {
        if (!hasUnsaved) {
            return;
        }
        const warn = (event) => {
            if (getLoggedUser() !== null) {
                event.preventDefault();
                // navegador antigo so pergunta com o returnValue
                event.returnValue = "";
            }
        };
        window.addEventListener("beforeunload", warn);
        return () => window.removeEventListener("beforeunload", warn);
    }, [hasUnsaved]);
}
