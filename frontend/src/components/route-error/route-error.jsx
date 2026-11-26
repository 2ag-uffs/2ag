import {useEffect, useRef, useState} from "react";
import {isRouteErrorResponse, Link, useRouteError} from "react-router";
import {FiAlertTriangle} from "react-icons/fi";
import {StatusTitle} from "../../app/page-title.jsx";
import {homePathFor} from "../../app/role-home.js";
import {CONNECTION_ERROR_MESSAGE, getLoggedUser} from "../../services/api.js";
import styles from "./route-error.module.css";

function reloadPage() {
    window.location.reload();
}

// erro de uma tela aparece so no lugar dela e o menu continua em volta
// assim a pessoa tenta de novo ou vai pra outra tela sem se perder
export default function RouteError() {
    const error = useRouteError();
    // quantas vezes o Tentar de novo foi tocado sem rede
    const [offlineTries, setOfflineTries] = useState(0);
    const messageRef = useRef(null);
    const offline = offlineTries > 0;
    const homePath = homePathFor(getLoggedUser());
    const isMissingPage = isRouteErrorResponse(error) && error.status === 404;
    const title = isMissingPage ? "Esta tela não existe" : "Não foi possível abrir esta tela";
    let message = "Tente de novo em instantes. Se continuar acontecendo, fale com a clínica.";
    if (isMissingPage) {
        message = "O endereço pode ter mudado. Use o menu para chegar onde você queria.";
    } else if (offline) {
        message = CONNECTION_ERROR_MESSAGE;
    }

    // o detalhe do erro fica no console pra quem for investigar
    useEffect(() => {
        console.error(error);
    }, [error]);

    // qnd a rede volta a tela recarrega sozinha, uma vez
    useEffect(() => {
        if (isMissingPage) {
            return undefined;
        }
        window.addEventListener("online", reloadPage, {once: true});
        return () => window.removeEventListener("online", reloadPage);
    }, [isMissingPage]);

    // a partir do segundo toque sem rede a frase ja eh a msm, entao cada toque leva o foco pro aviso
    // e o leitor de tela le ele de novo
    useEffect(() => {
        if (offlineTries > 0) {
            messageRef.current.focus();
        }
    }, [offlineTries]);

    // sem rede o recarregar trocava o 2AG pela pagina de erro do navegador, sem menu e sem volta
    function tryAgain() {
        if (navigator.onLine) {
            reloadPage();
        } else {
            setOfflineTries((tries) => tries + 1);
        }
    }

    return (
        <section className={styles.box} role="alert">
            <StatusTitle name={title}/>
            <FiAlertTriangle className={styles.icon} aria-hidden="true"/>
            <h1 className={styles.title}>{title}</h1>
            {/* sem rede o aviso fica em destaque */}
            <p ref={messageRef} tabIndex={-1} className={offline ? "aviso aviso--atencao" : styles.message}>
                {message}
            </p>
            <div className={styles.actions}>
                {!isMissingPage && (
                    <button type="button" className="button" onClick={tryAgain}>
                        Tentar de novo
                    </button>
                )}
                <Link to={homePath} className="button-secondary">
                    Ir para o início
                </Link>
            </div>
        </section>
    );
}
