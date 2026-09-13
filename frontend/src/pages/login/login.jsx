import {useState} from "react";
import {Link, useLocation, useNavigate, useSearchParams} from "react-router";
import AuthLayout from "../../components/auth-layout/auth-layout.jsx";
import PasswordField from "../../components/form/password-field.jsx";
import TextField from "../../components/form/text-field.jsx";
import {homePathFor} from "../../app/role-home.js";
import {apiService, ApiError, setLoggedUser} from "../../services/api.js";
import styles from "./login.module.css";

// aviso q outra tela pediu pro login mostrar
function findNotice(searchParams, location) {
    if (searchParams.get("sessao") === "expirada") {
        return "Sua sessão expirou. Entre de novo para continuar de onde parou.";
    }
    if (location.state && location.state.notice) {
        return location.state.notice;
    }
    return null;
}

// so aceita voltar pra uma tela do proprio sistema
// senao alguem podia montar um link de login q manda a pessoa pra outro site
function findReturnPath(searchParams) {
    const returnPath = searchParams.get("voltar");
    if (!returnPath || !returnPath.startsWith("/") || returnPath.startsWith("//") || returnPath.includes("\\")) {
        return null;
    }
    return returnPath;
}

export default function Login() {
    const navigate = useNavigate();
    const location = useLocation();
    const [searchParams] = useSearchParams();

    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [errorMessage, setErrorMessage] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const notice = findNotice(searchParams, location);

    const handleSubmit = async (event) => {
        event.preventDefault();
        setIsSubmitting(true);
        setErrorMessage(null);

        try {
            const user = await apiService.post("/auth/login", {email: email, password: password});
            setLoggedUser(user);
            // quem caiu aqui pq a sessao acabou volta pra tela onde estava
            const returnPath = findReturnPath(searchParams);
            navigate(returnPath || homePathFor(user), {replace: true});
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                setErrorMessage(requestError.message);
            } else {
                setErrorMessage("Não foi possível falar com o servidor. Confira sua internet e tente de novo.");
            }
            setIsSubmitting(false);
        }
    };

    return (
        <AuthLayout title="Entrar">
            {notice && !errorMessage && <p className={"aviso " + styles.message}>{notice}</p>}
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
                    required={true}
                />
                <PasswordField
                    label="Senha"
                    name="password"
                    autoComplete="current-password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    required={true}
                />
                <Link to="/esqueci-senha" className={styles.forgotLink}>Esqueci minha senha</Link>
                <button type="submit" className={styles.submitButton} disabled={isSubmitting}>
                    {isSubmitting ? "Entrando..." : "Entrar"}
                </button>
            </form>

            <p className={styles.signUpNote}>
                É paciente e ainda não tem conta? O cadastro é feito pelo link de convite que o seu prescritor envia.
            </p>
        </AuthLayout>
    );
}
