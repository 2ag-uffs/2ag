import {Link} from "react-router";
import {StatusTitle} from "../../app/page-title.jsx";
import {homePathFor} from "../../app/role-home.js";
import {getLoggedUser} from "../../services/api.js";
import styles from "./status-page.module.css";

// tela simples pra rota q n existe ou erro inesperado
// sem o link do inicio ela abre tbm fora das rotas, como qnd a api n responde e as rotas nem montam
export default function StatusPage({title, message, showReloadButton = false, showHomeLink = true}) {
    const homePath = homePathFor(getLoggedUser());

    return (
        <main className={styles.page}>
            <StatusTitle name={title}/>
            <img src="/images/logotipo-horizontal.svg" alt="2AG" className={styles.logo}/>
            <h1 className={styles.title}>{title}</h1>
            <p className={styles.message}>{message}</p>
            <div className={styles.actions}>
                {showReloadButton && (
                    <button type="button" className={styles.action} onClick={() => window.location.reload()}>
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
