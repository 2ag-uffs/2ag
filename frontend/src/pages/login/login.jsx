import {useState} from "react";
import {useLocation, useNavigate} from "react-router";
import "./login.css";
import {homePathFor} from "../../app/role-home.js";
import {apiService, setLoggedUser, ApiError} from "../../services/api.js";

export default function Login() {
    const navigate = useNavigate();
    const location = useLocation();
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);
    // quem acabou de se cadastrar chega aqui com esse aviso
    const justRegistered = Boolean(location.state && location.state.cadastrado);

    const handleSubmit = async (event) => {
        event.preventDefault();
        setIsLoading(true);
        setError(null);

        const email = event.target.email.value;
        const password = event.target.password.value;

        try {
            // quem tenta entrar de novo comeca sem sessao na tela
            setLoggedUser(null);
            const user = await apiService.post("/auth/login", {email: email, password: password});
            setLoggedUser(user);
            navigate(homePathFor(user), {replace: true});
        } catch (requestError) {
            if (requestError instanceof ApiError) {
                setError(requestError.message);
            } else {
                setError("Não foi possível falar com o servidor.");
            }
        } finally {
            setIsLoading(false);
        }
    };

    const handleSignUp = (event) => {
        event.preventDefault();
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
                    {justRegistered && !error && (
                        <p className="aviso">Cadastro feito. Entre com seu e-mail e senha.</p>
                    )}
                    {error && <p className="login__error-message">{error}</p>}
                    <form className="login__content__form" onSubmit={handleSubmit}>
                        <div className="login__content__form__input-group">
                            <label htmlFor="email">Email</label>
                            <input
                                id="email"
                                name="email"
                                type="email"
                                autoComplete="username"
                                required={true}
                            />
                        </div>
                        <div className="login__content__form__input-group">
                            <label htmlFor="password">Senha</label>
                            <input
                                id="password"
                                name="password"
                                type="password"
                                autoComplete="current-password"
                                required={true}
                            />
                        </div>
                        <div className="login__content__form__actions">
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
