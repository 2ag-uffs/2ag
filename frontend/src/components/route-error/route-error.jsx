import {useEffect} from "react";
import {isRouteErrorResponse, Link, useRouteError} from "react-router";
import {FiAlertTriangle} from "react-icons/fi";
import {homePathFor} from "../../app/role-home.js";
import {getLoggedUser} from "../../services/api.js";
import styles from "./route-error.module.css";

// erro de uma tela aparece so no lugar dela e o menu continua em volta
// assim a pessoa tenta de novo ou vai pra outra tela sem se perder
export default function RouteError() {
    const error = useRouteError();
    const homePath = homePathFor(getLoggedUser());
    const isMissingPage = isRouteErrorResponse(error) && error.status === 404;

    // o detalhe do erro fica no console pra quem for investigar
    useEffect(() => {
        console.error(error);
    }, [error]);

    return (
        <section className={styles.box} role="alert">
            <FiAlertTriangle className={styles.icon} aria-hidden="true"/>
            <h1 className={styles.title}>
                {isMissingPage ? "Esta tela não existe" : "Não foi possível abrir esta tela"}
            </h1>
            <p className={styles.message}>
                {isMissingPage
                    ? "O endereço pode ter mudado. Use o menu para chegar onde você queria."
                    : "Tente de novo em instantes. Se continuar acontecendo, fale com a clínica."}
            </p>
            <div className={styles.actions}>
                {!isMissingPage && (
                    <button type="button" className="button" onClick={() => window.location.reload()}>
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
