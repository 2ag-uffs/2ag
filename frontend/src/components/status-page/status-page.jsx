import {useEffect, useRef, useState} from "react";
import {Link} from "react-router";
import {StatusTitle} from "../../app/page-title.jsx";
import {homePathFor} from "../../app/role-home.js";
import {CONNECTION_ERROR_MESSAGE, getLoggedUser} from "../../services/api.js";
import styles from "./status-page.module.css";

function reloadPage() {
    window.location.reload();
}

// tela simples pra rota q n existe ou erro inesperado
// sem o link do inicio ela abre tbm fora das rotas, como qnd a api n responde e as rotas nem montam
export default function StatusPage({title, message, showReloadButton = false, showHomeLink = true}) {
    // quantas vezes o Tentar de novo foi tocado sem rede
    const [offlineTries, setOfflineTries] = useState(0);
    const messageRef = useRef(null);
    const homePath = homePathFor(getLoggedUser());
    const offline = offlineTries > 0;

    // com o Tentar de novo na tela, qnd a rede volta ela recarrega sozinha, uma vez
    useEffect(() => {
        if (!showReloadButton) {
            return undefined;
        }
        window.addEventListener("online", reloadPage, {once: true});
        return () => window.removeEventListener("online", reloadPage);
    }, [showReloadButton]);

    // a api sem resposta ja mostra a frase de sem internet, entao o toque sem rede n mudava nada na tela
    // cada toque leva o foco pro aviso e o leitor de tela le ele de novo
    useEffect(() => {
        if (offlineTries > 0) {
            messageRef.current.focus();
        }
    }, [offlineTries]);

    // sem rede o recarregar trocava o 2AG pela pagina de erro do navegador, sem volta
    function tryAgain() {
        if (navigator.onLine) {
            reloadPage();
        } else {
            setOfflineTries((tries) => tries + 1);
        }
    }

    return (
        <main className={styles.page}>
            <StatusTitle name={title}/>
            {/* so uma das duas aparece: a verde no tema claro e a de palha no escuro (styles/colors.css) */}
            <img src="/images/logotipo-horizontal.svg" alt="2AG" className={"logo-on-light " + styles.logo}/>
            <img src="/images/logotipo-horizontal-claro.svg" alt="2AG" className={"logo-on-dark " + styles.logo}/>
            <h1 className={styles.title}>{title}</h1>
            {/* sem rede o aviso fica em destaque */}
            <p
                ref={messageRef}
                tabIndex={-1}
                className={offline ? "aviso aviso--atencao " + styles.offlineMessage : styles.message}
            >
                {offline ? CONNECTION_ERROR_MESSAGE : message}
            </p>
            <div className={styles.actions}>
                {showReloadButton && (
                    <button type="button" className={styles.action} onClick={tryAgain}>
                        Tentar de novo
                    </button>
                )}
                {/* sozinho na tela o link do inicio eh a acao principal. do lado do Tentar de novo ele fica de contorno */}
                {showHomeLink && (
                    <Link to={homePath} className={(showReloadButton ? "button button-secondary " : "button ") + styles.action}>
                        Ir para o início
                    </Link>
                )}
            </div>
        </main>
    );
}
