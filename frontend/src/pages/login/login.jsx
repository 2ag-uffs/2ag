import {useState} from "react";
import {useLocation, useNavigate} from "react-router";
import "./login.css";
import {apiService, saveToken, getLoggedUser, ApiError} from "../../services/api.js";

export default function Login() {
    const navigate = useNavigate();
    const location = useLocation();
    // estados pra controlar o carregamento e os erros
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);
    // quem acabou de se cadastrar cai aqui com esse aviso, em vez do
    // alert que a tela de cadastro dava antes
    const cadastrado = Boolean(location.state && location.state.cadastrado);

    // alterei a função handleSubmit pra assíncrona, agora a gente consegue usar await para esperar a resposta da API
    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsLoading(true);
        setError(null);

        const email = e.target.email.value;
        const password = e.target.password.value;

        try {
            const data = await apiService.post("/auth/login", {email: email, senha: password});
            saveToken(data.token);

            const userRole = getLoggedUser()?.authorities?.[0];

            if (userRole === "ROLE_PATIENT") {
                navigate("/dashboard-paciente");
            } else if (userRole === "ROLE_PRESCRIBER") {
                navigate("/dashboard-prescritor");
            } else {
                setError("perfil de usuário não reconhecido no token");
            }

        } catch (err) {
            setError(err instanceof ApiError ? err.message : "falha de conexão com o servidor");
        }

        setIsLoading(false);
    };

    const handleSignUp = (e) => {
        e.preventDefault();
        navigate("/sign-up");
    };

    return (
        <div className="login">
            <section className="login__art">
                <img
                    alt="Logotipo 2AG"
                    className="login__art__top-left"
                    src="/images/logotipo-icon-claro.svg"
                />
                <img
                    alt="Logotipo 2AG"
                    className="login__art__top-right"
                    src="/images/logotipo-icon-claro.svg"
                />
                <img
                    alt="Logotipo 2AG"
                    className="login__art__center"
                    src="/images/logotipo-vertical-claro.svg"
                />
                <img
                    alt="Logotipo 2AG"
                    className="login__art__bottom-left"
                    src="/images/logotipo-icon-claro.svg"
                />
                <img
                    alt="Logotipo 2AG"
                    className="login__art__bottom-right"
                    src="/images/logotipo-icon-claro.svg"
                />
            </section>
            <section className="login__content">
                <div className="login__content-wrapper">
                    <img
                        alt="Logotipo 2AG"
                        className="login__content__logo"
                        src="/images/logotipo-horizontal.svg"
                    />
                    <h2 className="login__content__title">Login</h2>
                    {cadastrado && !error && (
                        <p className="aviso">Cadastro feito. Entre com seu e-mail e senha.</p>
                    )}
                    {/* adiconei para mostrar o erro (resp: maiqueli) */}
                    {error && <p className="login__error-message">{error}</p>}
                    <form className="login__content__form" onSubmit={handleSubmit}>
                        <div className="login__content__form__input-group">
                            <label htmlFor="email">Email</label>
                            <input
                                id="email"
                                name="email"
                                type="email"
                                required={true}
                            />
                        </div>
                        <div className="login__content__form__input-group">
                            <label htmlFor="password">Senha</label>
                            <input
                                id="password"
                                name="password"
                                type="password"
                                required={true}
                            />
                        </div>
                        <div className="login__content__form__actions">
                            {/* usei o estado isLoading no botão (resp: maiqueli) */}
                            <button type="submit" disabled={isLoading}>
                                {isLoading ? "Entrando..." : "Entrar"}
                            </button>
                            <button className="button-secondary" type="button" onClick={handleSignUp}>
                                Criar conta
                            </button>
                        </div>
                    </form>
                </div>
            </section>
        </div>
    );
}