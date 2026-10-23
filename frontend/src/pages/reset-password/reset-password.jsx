import {useEffect, useState} from "react";
import {Link, useNavigate, useSearchParams} from "react-router";
import AuthLayout from "../../components/auth-layout/auth-layout.jsx";
import PasswordChecklist from "../../components/form/password-checklist.jsx";
import PasswordField from "../../components/form/password-field.jsx";
import {isStrongPassword} from "../../components/form/password-rules.js";
import {homePathFor} from "../../app/role-home.js";
import {apiService, ApiError, getLoggedUser, logout} from "../../services/api.js";
import styles from "./reset-password.module.css";

// senha nova pelo link q chegou por e-mail (RF35), tbm o link de primeiro acesso do prescritor
export default function ResetPassword() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const tokenInUrl = searchParams.get("token");
    // o codigo fica guardado na tela depois de sair da barra de endereco
    const [token] = useState(tokenInUrl);
    // o link aberto com outra conta logada explica q precisa sair dela antes
    const [otherAccount, setOtherAccount] = useState(() => getLoggedUser());
    const [logoutError, setLogoutError] = useState(null);

    const [newPassword, setNewPassword] = useState("");
    const [passwordConfirmation, setPasswordConfirmation] = useState("");
    const [fieldErrors, setFieldErrors] = useState({});
    const [errorMessage, setErrorMessage] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    // tira o codigo da barra de endereco e do historico do navegador
    // com outra conta aberta ele fica, pq sem o codigo a rota manda pra tela inicial dessa conta
    useEffect(() => {
        if (tokenInUrl && !otherAccount) {
            navigate("/redefinir-senha", {replace: true});
        }
    }, [tokenInUrl, otherAccount, navigate]);

    // sai da conta q estava aberta e segue com o mesmo link
    const logOutOtherAccount = async () => {
        setLogoutError(null);
        const isLoggedOut = await logout();
        if (isLoggedOut) {
            setOtherAccount(null);
        } else {
            setLogoutError("Não foi possível sair da conta. Confira sua internet e tente de novo.");
        }
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setErrorMessage(null);

        const formErrors = {};
        if (!isStrongPassword(newPassword)) {
            formErrors.newPassword = "A senha ainda não cumpre todas as regras";
        }
        if (newPassword !== passwordConfirmation) {
            formErrors.passwordConfirmation = "As senhas não são iguais";
        }
        setFieldErrors(formErrors);
        if (Object.keys(formErrors).length > 0) {
            return;
        }

        setIsSubmitting(true);
        try {
            await apiService.post("/auth/password-reset/confirm", {token: token, newPassword: newPassword});
            navigate("/entrar", {replace: true, state: {notice: "Senha nova criada. Entre com ela."}});
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                const errorsByField = requestError.fieldErrors();
                setFieldErrors(errorsByField);
                if (Object.keys(errorsByField).length === 0) {
                    setErrorMessage(requestError.message);
                }
            } else {
                setErrorMessage("Não foi possível falar com o servidor. Confira sua internet e tente de novo.");
            }
            setIsSubmitting(false);
        }
    };

    if (!token) {
        return (
            <AuthLayout title="Criar senha nova">
                <p className={"aviso aviso--atencao " + styles.message}>
                    Este link está incompleto. Abra de novo o link que chegou no seu e-mail ou peça um novo.
                </p>
                <Link to="/esqueci-senha" className={"button button-secondary " + styles.linkButton}>
                    Pedir um novo link
                </Link>
            </AuthLayout>
        );
    }

    if (otherAccount) {
        return (
            <AuthLayout title="Criar senha nova">
                <p className={"aviso aviso--atencao " + styles.message}>
                    Você está conectado como <strong>{otherAccount.name}</strong> neste navegador. Para criar a senha
                    com este link, saia dessa conta antes.
                </p>
                {logoutError && <p className={"aviso aviso--atencao " + styles.message} role="alert">{logoutError}</p>}
                <button type="button" className="button" onClick={logOutOtherAccount}>
                    Sair e continuar
                </button>
                <Link
                    to={homePathFor(otherAccount)}
                    replace={true}
                    className={"button button-secondary " + styles.linkButton}
                >
                    Voltar para a conta aberta
                </Link>
            </AuthLayout>
        );
    }

    return (
        <AuthLayout title="Criar senha nova">
            {errorMessage && (
                <div className={styles.errorBox}>
                    <p className={"aviso aviso--atencao " + styles.message} role="alert">{errorMessage}</p>
                    <Link to="/esqueci-senha" className={styles.textLink}>Pedir um novo link</Link>
                </div>
            )}

            <form className={styles.form} onSubmit={handleSubmit}>
                <PasswordField
                    label="Nova senha"
                    name="newPassword"
                    autoComplete="new-password"
                    value={newPassword}
                    onChange={(event) => setNewPassword(event.target.value)}
                    error={fieldErrors.newPassword}
                    required={true}
                />
                <PasswordChecklist password={newPassword}/>
                <PasswordField
                    label="Repita a nova senha"
                    name="passwordConfirmation"
                    autoComplete="new-password"
                    value={passwordConfirmation}
                    onChange={(event) => setPasswordConfirmation(event.target.value)}
                    error={fieldErrors.passwordConfirmation}
                    required={true}
                />
                <button type="submit" className={styles.submitButton} disabled={isSubmitting}>
                    {isSubmitting ? "Salvando..." : "Salvar senha nova"}
                </button>
            </form>
        </AuthLayout>
    );
}
