import {useEffect, useRef, useState} from "react";
import {Link, useNavigate, useSearchParams} from "react-router";
import AuthLayout from "../../components/auth-layout/auth-layout.jsx";
import BusyButton from "../../components/form/busy-button.jsx";
import {ApiError, apiService, errorMessageOf, getLoggedUser, logout} from "../../services/api.js";
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
    // a api respondeu q o link n serve: confirmar de novo ia falhar igual, a saida eh pedir a troca de novo
    const [isRefused, setIsRefused] = useState(false);
    const askAgainRef = useRef(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    // a conta q estava aberta neste navegador e ficou, pq a api n confirmou a saida dela dps da confirmacao
    const [openAccount, setOpenAccount] = useState(null);

    // tira o codigo da barra de endereco e do historico do navegador
    useEffect(() => {
        if (tokenInUrl) {
            navigate("/confirmar-email", {replace: true});
        }
    }, [tokenInUrl, navigate]);

    // a api so encerra as sessoes da conta do link, e a conta aberta neste navegador pode ser outra
    // (responsavel com dois filhos). a tela sai dela pela api e so vai pro login qnd a api confirma a saida,
    // senao o cookie continuava valendo e a proxima pessoa no computador entrava sem senha
    const leaveOpenAccount = async () => {
        const loggedUser = getLoggedUser();
        setIsSubmitting(true);
        if (loggedUser === null || await logout()) {
            navigate("/entrar", {replace: true, state: {notice: "E-mail confirmado. Entre com o e-mail novo."}});
            return;
        }
        setOpenAccount(loggedUser);
        setIsSubmitting(false);
    };

    // o botao de confirmar sai da tela com a recusa, entao o foco do teclado vai pro botao q ficou no lugar
    useEffect(() => {
        if (isRefused && askAgainRef.current) {
            askAgainRef.current.focus();
        }
    }, [isRefused]);

    const handleConfirm = async () => {
        setErrorMessage(null);
        setIsSubmitting(true);
        try {
            await apiService.post("/auth/email-change/confirm", {token: token});
        } catch (requestError) {
            setErrorMessage(errorMessageOf(requestError));
            // sem conexao, erro do sistema e limite de pedidos n sao recusa do link: vale tentar de novo
            setIsRefused(requestError instanceof ApiError && requestError.status < 500 && requestError.status !== 429);
            setIsSubmitting(false);
            return;
        }
        await leaveOpenAccount();
    };

    if (!token) {
        return (
            <AuthLayout title="Confirmar e-mail novo">
                <p className={"aviso aviso--atencao " + styles.message} role="alert">
                    Este link está incompleto. Abra de novo o link que chegou no seu e-mail novo ou peça a troca
                    de novo no seu perfil.
                </p>
                <Link to="/perfil" className={"button button-secondary " + styles.linkButton}>
                    Abrir o perfil
                </Link>
            </AuthLayout>
        );
    }

    // link recusado: o confirmar some e o Pedir a troca de novo vira o botao principal embaixo do aviso
    // (decisao da dona, nov/2026)
    if (isRefused) {
        return (
            <AuthLayout title="Confirmar e-mail novo">
                <p className={"aviso aviso--atencao " + styles.message} role="alert">{errorMessage}</p>
                <Link to="/perfil" className={"button " + styles.askAgainButton} ref={askAgainRef}>
                    Pedir a troca de novo
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

            {openAccount && (
                <p className={"aviso aviso--atencao " + styles.message} role="alert">
                    O e-mail novo foi confirmado, mas não foi possível sair da conta
                    de <strong>{openAccount.name}</strong> neste navegador. Confira sua internet e tente de novo.
                </p>
            )}
            {!openAccount && (
                <p className={styles.message}>
                    Você pediu para usar este e-mail como acesso da sua conta no 2AG. Ao confirmar, o e-mail antigo
                    deixa de entrar e as sessões abertas são encerradas: entre de novo com o e-mail novo.
                </p>
            )}
            {/* o botao eh o mesmo nos dois casos, pro foco do teclado continuar nele (CT-39) */}
            <BusyButton
                type="button"
                className={styles.submitButton}
                onClick={openAccount ? leaveOpenAccount : handleConfirm}
                isBusy={isSubmitting}
                busyText={openAccount ? "Saindo..." : "Confirmando..."}
            >
                {openAccount ? "Sair da conta" : "Confirmar e-mail novo"}
            </BusyButton>
        </AuthLayout>
    );
}
