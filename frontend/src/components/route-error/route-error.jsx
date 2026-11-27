import {useEffect} from "react";
import {isRouteErrorResponse, useRouteError} from "react-router";
import {StatusTitle} from "../../app/page-title.jsx";
import ErrorCard from "../error-card/error-card.jsx";

// erro de uma tela aparece so no lugar dela e o menu continua em volta
// assim a pessoa tenta de novo ou vai pra outra tela sem se perder
// o cartao eh o msm da falha ao carregar (components/error-card), q cuida do Tentar de novo sem rede
export default function RouteError() {
    const error = useRouteError();
    const isMissingPage = isRouteErrorResponse(error) && error.status === 404;
    const title = isMissingPage ? "Esta tela não existe" : "Não foi possível abrir esta tela";
    const message = isMissingPage
        ? "O endereço pode ter mudado. Use o menu para chegar onde você queria."
        : "Tente de novo em instantes. Se continuar acontecendo, fale com a clínica.";

    // o detalhe do erro fica no console pra quem for investigar
    useEffect(() => {
        console.error(error);
    }, [error]);

    return (
        <>
            <StatusTitle name={title}/>
            <ErrorCard title={title} message={message} showRetry={!isMissingPage} showHome={true}/>
        </>
    );
}
