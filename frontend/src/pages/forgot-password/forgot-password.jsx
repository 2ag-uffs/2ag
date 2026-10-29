import {useState} from "react";
import {Link} from "react-router";
import AuthLayout from "../../components/auth-layout/auth-layout.jsx";
import TextField from "../../components/form/text-field.jsx";
import TrapField from "../../components/form/trap-field.jsx";
import {apiService, readRequestError} from "../../services/api.js";
import styles from "./forgot-password.module.css";

// pedido do link pra criar uma senha nova (RF35)
export default function ForgotPassword() {
    const [email, setEmail] = useState("");
    const [site, setSite] = useState("");
    const [fieldErrors, setFieldErrors] = useState({});
    const [errorMessage, setErrorMessage] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [wasSent, setWasSent] = useState(false);

    const handleSubmit = async (event) => {
        event.preventDefault();
        setIsSubmitting(true);
        setFieldErrors({});
        setErrorMessage(null);

        try {
            await apiService.post("/auth/password-reset/request", {email: email, site: site});
            setWasSent(true);
        } catch (requestError) {
            const errorInfo = readRequestError(requestError);
            setFieldErrors(errorInfo.fieldErrors);
            if (errorInfo.message) {
                setErrorMessage(errorInfo.message);
            }
        } finally {
            setIsSubmitting(false);
        }
    };

    // a resposta eh a mesma exista ou n uma conta com esse e-mail
    if (wasSent) {
        return (
            <AuthLayout title="Confira seu e-mail">
                <p className={"aviso " + styles.message}>
                    Se houver uma conta com o e-mail <strong>{email}</strong>, o link para criar uma senha nova chega
                    em alguns minutos. Ele vale por 30 minutos.
                </p>
                <p className={styles.note}>Não chegou? Confira a caixa de spam ou peça de novo daqui a pouco.</p>
                <Link to="/entrar" className={"button button-secondary " + styles.linkButton}>
                    Voltar para o login
                </Link>
            </AuthLayout>
        );
    }

    return (
        <AuthLayout title="Recuperar senha">
            <p className={styles.note}>
                Informe o e-mail da sua conta. Vamos mandar um link para você criar uma senha nova.
            </p>

            {errorMessage && (
                <p className={"aviso aviso--atencao " + styles.message} role="alert">{errorMessage}</p>
            )}

            <form className={styles.form} onSubmit={handleSubmit}>
                <TextField
                    label="E-mail"
                    name="email"
                    type="email"
                    inputMode="email"
                    autoComplete="username"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    error={fieldErrors.email}
                    required={true}
                />
                <TrapField value={site} onChange={setSite}/>
                <button type="submit" className={styles.submitButton} disabled={isSubmitting}>
                    {isSubmitting ? "Enviando..." : "Enviar link"}
                </button>
            </form>

            <Link to="/entrar" className={styles.backLink}>Voltar para o login</Link>
        </AuthLayout>
    );
}
