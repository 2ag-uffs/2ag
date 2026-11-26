import {useEffect, useState} from "react";
import {getLoggedUser, hasSendWaitingLogin} from "../services/api.js";

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

// a sessao venceu e o envio da tela esta parado atras do login por cima (decisao de novembro/2026)
// enquanto salva as telas n perguntam antes de sair, mas o envio parado ali ainda n foi gravado:
// quem recarrega perde o q digitou, e quem volta n ve se gravou e pode emitir a receita de novo
export function useSendWaitingLogin() {
    const [isWaiting, setIsWaiting] = useState(hasSendWaitingLogin);
    useEffect(() => {
        const update = () => setIsWaiting(hasSendWaitingLogin());
        window.addEventListener("waiting-login-changed", update);
        update();
        return () => window.removeEventListener("waiting-login-changed", update);
    }, []);
    return isWaiting;
}

// a saida barrada atras do login por cima fica com a pergunta escondida junto com a tela
// qnd a pessoa entra de novo e o envio segue, a pergunta eh esquecida: senao ela aparecia
// em cima do resultado, falando em perder o q acabou de ser salvo
export function useForgetBlockedExit(exitBlocker, hasUnsaved) {
    useEffect(() => {
        if (!hasUnsaved && exitBlocker.state === "blocked") {
            exitBlocker.reset();
        }
    }, [hasUnsaved, exitBlocker]);
}
