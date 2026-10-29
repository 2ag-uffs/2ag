import {useEffect, useState} from "react";
import {Link, useNavigate, useSearchParams} from "react-router";
import AuthLayout from "../../components/auth-layout/auth-layout.jsx";
import {apiService, errorMessageOf, setLoggedUser} from "../../services/api.js";
import styles from "./confirm-email.module.css";

// confirmacao do e-mail de acesso novo pelo link q chegou nele (issue 130)
// a confirmacao so acontece no clique: leitor de e-mail q abre link sozinho n gasta o link
export default function ConfirmEmail() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const tokenInUrl = searchParams.get("token");
    // o codigo fica guardado na tela depois de sair da barra de endereco
    const [token] = useState(tokenInUrl);

    const [errorMessage, setErrorMessage] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    // tira o codigo da barra de endereco e do historico do navegador
    useEffect(() => {
        if (tokenInUrl) {
            navigate("/confirmar-email", {replace: true});
        }
    }, [tokenInUrl, navigate]);

    const handleConfirm = async () => {
        setErrorMessage(null);
        setIsSubmitting(true);
        try {
            await apiService.post("/auth/email-change/confirm", {token: token});
            // a api encerra as sessoes abertas, entao a tela esquece quem estava logado
            setLoggedUser(null);
            navigate("/entrar", {replace: true, state: {notice: "E-mail confirmado. Entre com o e-mail novo."}});
        } catch (requestError) {
            setErrorMessage(errorMessageOf(requestError));
            setIsSubmitting(false);
        }
    };

    if (!token) {
        return (
            <AuthLayout title="Confirmar e-mail novo">
                <p className={"aviso aviso--atencao " + styles.message}>
                    Este link está incompleto. Abra de novo o link que chegou no seu e-mail novo ou peça a troca
                    de novo no seu perfil.
                </p>
                <Link to="/perfil" className={"button button-secondary " + styles.linkButton}>
                    Abrir o perfil
                </Link>
            </AuthLayout>
        );
    }

    return (
        <AuthLayout title="Confirmar e-mail novo">
            {errorMessage && (
                <div className={styles.errorBox}>
                    <p className={"aviso aviso--atencao " + styles.message} role="alert">{errorMessage}</p>
                    <Link to="/perfil" className={styles.textLink}>Pedir a troca de novo</Link>
                </div>
            )}

            <p className={styles.message}>
                Você pediu para usar este e-mail como acesso da sua conta no 2AG. Ao confirmar, o e-mail antigo
                deixa de entrar e as sessões abertas são encerradas: entre de novo com o e-mail novo.
            </p>
            <button type="button" className={styles.submitButton} onClick={handleConfirm} disabled={isSubmitting}>
                {isSubmitting ? "Confirmando..." : "Confirmar e-mail novo"}
            </button>
        </AuthLayout>
    );
}
