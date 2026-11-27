import {useEffect, useRef, useState} from "react";
import {Link} from "react-router";
import {FiAlertTriangle, FiLock} from "react-icons/fi";
import {homePathFor} from "../../app/role-home.js";
import {CONNECTION_ERROR_MESSAGE, getLoggedUser} from "../../services/api.js";
import styles from "./error-card.module.css";

const LOAD_FAILED_TITLE = "Esta tela não carregou";
const DENIED_TITLE = "Sem acesso a esta tela";

function reloadPage() {
    window.location.reload();
}

// cartao unico de erro dentro da tela, com o menu em volta (decisao da dona, nov/2026)
// falha ao carregar: Tentar de novo. falta de acesso: cadeado e Ir para o inicio
// a tela q n abriu (route-error) usa o msm cartao com os dois botoes
// embaixo do cabecalho da tela o titulo do cartao eh h2, sozinho na tela eh h1
export default function ErrorCard({title, message, isDenied = false, showRetry, showHome, isUnderPageHeader = false}) {
    // quantas vezes o Tentar de novo foi tocado sem rede
    const [offlineTries, setOfflineTries] = useState(0);
    const messageRef = useRef(null);
    const offline = offlineTries > 0;
    const hasRetry = showRetry === undefined ? !isDenied : showRetry;
    const hasHome = showHome === undefined ? isDenied : showHome;
    const Icon = isDenied ? FiLock : FiAlertTriangle;
    const Title = isUnderPageHeader ? "h2" : "h1";

    // com o Tentar de novo no cartao q fica no lugar da tela, qnd a rede volta ela recarrega sozinha, uma vez
    // embaixo do cabecalho a tela continua viva em volta (modal com link de primeiro acesso, convite, filtro),
    // entao ali so o toque no Tentar de novo recarrega
    const reloadsWhenOnline = hasRetry && !isUnderPageHeader;
    useEffect(() => {
        if (!reloadsWhenOnline) {
            return undefined;
        }
        window.addEventListener("online", reloadPage, {once: true});
        return () => window.removeEventListener("online", reloadPage);
    }, [reloadsWhenOnline]);

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
            <Icon className={styles.icon} aria-hidden="true"/>
            <Title className={styles.title}>{title || (isDenied ? DENIED_TITLE : LOAD_FAILED_TITLE)}</Title>
            {/* sem rede o aviso fica em destaque */}
            <p ref={messageRef} tabIndex={-1} className={offline ? "aviso aviso--atencao" : styles.message}>
                {offline ? CONNECTION_ERROR_MESSAGE : message}
            </p>
            <div className={styles.actions}>
                {hasRetry && (
                    <button type="button" className="button" onClick={tryAgain}>
                        Tentar de novo
                    </button>
                )}
                {/* sozinho no cartao o link do inicio eh a acao principal. do lado do Tentar de novo ele fica de contorno */}
                {hasHome && (
                    <Link to={homePathFor(getLoggedUser())} className={hasRetry ? "button-secondary" : "button"}>
                        Ir para o início
                    </Link>
                )}
            </div>
        </section>
    );
}

// erro de carregar a tela como o loadErrorOf do api.js devolve: a frase e se foi falta de acesso
export function LoadError({error, isUnderPageHeader = false}) {
    return <ErrorCard message={error.message} isDenied={error.isDenied} isUnderPageHeader={isUnderPageHeader}/>;
}
