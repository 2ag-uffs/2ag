import {useState} from "react";
import PasswordField from "../form/password-field.jsx";
import TextField from "../form/text-field.jsx";
import {errorMessageOf, getLoggedUser, loginAgain} from "../../services/api.js";
import styles from "./login-again.module.css";

// a sessao venceu com uma tela aberta: a pessoa entra de novo por cima dela, so na msm conta,
// e o envio q tinha parado segue de onde estava (#151)
// o e-mail n muda pq outra conta n pode receber o q esta na tela de baixo
// conta desativada n entra mais: no lugar do login vai o motivo, sem prometer enviar nada (CT-41)
export default function LoginAgain({deactivatedMessage, onLoggedIn, onLeave}) {
    const loggedUser = getLoggedUser();
    const [password, setPassword] = useState("");
    const [errorMessage, setErrorMessage] = useState(null);
    const [isWrongLogin, setIsWrongLogin] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    // a sessao venceu sozinha e a conta foi desativada nesse meio tempo: quem diz eh o 403 do login
    const [refusedMessage, setRefusedMessage] = useState(null);
    const accountMessage = deactivatedMessage || refusedMessage;

    const handleSubmit = async (event) => {
        event.preventDefault();
        setIsSubmitting(true);
        setErrorMessage(null);
        try {
            await loginAgain(password);
            onLoggedIn();
        } catch (requestError) {
            if (requestError.status === 403) {
                setRefusedMessage(errorMessageOf(requestError));
                return;
            }
            setErrorMessage(errorMessageOf(requestError));
            // o e-mail confirmado em outra aba encerra a sessao e o travado aqui eh o antigo,
            // entao a recusa diz como sair dali
            setIsWrongLogin(requestError.status === 401);
            setIsSubmitting(false);
        }
    };

    if (accountMessage) {
        return (
            <div className={styles.backdrop}>
                <div className={styles.box} role="dialog" aria-modal="true" aria-labelledby="login-again-title"
                     aria-describedby="login-again-reason">
                    <h2 id="login-again-title" className={styles.title}>Acesso encerrado</h2>
                    <p id="login-again-reason" className={styles.text}>{accountMessage}</p>
                    <div className={styles.actions}>
                        <button type="button" className="button" onClick={onLeave} autoFocus={true}>Sair</button>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className={styles.backdrop}>
            <form className={styles.box} role="dialog" aria-modal="true" aria-labelledby="login-again-title"
                  onSubmit={handleSubmit}>
                <h2 id="login-again-title" className={styles.title}>Sua sessão venceu</h2>
                <p className={styles.text}>
                    Entre de novo para continuar. O que você já preencheu não se perde e é enviado depois de
                    entrar.
                </p>
                {errorMessage && <p className="aviso aviso--atencao" role="alert">{errorMessage}</p>}
                <TextField
                    label="E-mail"
                    name="loginAgainEmail"
                    type="email"
                    autoComplete="username"
                    value={loggedUser ? loggedUser.email || "" : ""}
                    readOnly={true}
                    hint={isWrongLogin ? "Trocou o e-mail de acesso? Toque em Sair e entre com o e-mail novo." : null}
                />
                <PasswordField
                    label="Senha"
                    name="loginAgainPassword"
                    autoComplete="current-password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    required={true}
                    autoFocus={true}
                />
                <div className={styles.actions}>
                    <button type="button" className="button-secondary" onClick={onLeave}>Sair</button>
                    <button type="submit" className="button" disabled={isSubmitting}>
                        {isSubmitting ? "Entrando..." : "Entrar"}
                    </button>
                </div>
            </form>
        </div>
    );
}
