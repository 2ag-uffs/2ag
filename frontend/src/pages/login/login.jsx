import {useState} from "react";
import {useNavigate} from "react-router-dom";
import "./login.css";

// função auxiliar pra decodificar o token jwt
// ela pega a parte do meio do token que tem as infos do usuário ai sabemos o role dele
function parseJwt(token) {
    try {
        return JSON.parse(atob(token.split('.')[1]));
    } catch (e) {
        return null;
    }
}

export default function Login() {
    const navigate = useNavigate();
    // estados pra controlar o carregamento e os erros
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);

    // alterei a função handleSubmit pra assíncrona, agora a gente consegue usar await para esperar a resposta da API
    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsLoading(true);
        setError(null);

        const email = e.target.email.value;
        const password = e.target.password.value;

        try {
            const response = await fetch("http://localhost:8080/auth/login", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({email: email, senha: password}),
            });

            if (!response.ok) {
                setError("e-mail ou senha inválidos!");
                setIsLoading(false);
                return;
            }

            const data = await response.json();
            localStorage.setItem("authToken", data.token);

            const decodedToken = parseJwt(data.token);
            const userRole = decodedToken?.authorities?.[0];

            if (userRole === "ROLE_USER") {
                navigate("/dashboard-paciente");
            } else if (userRole === "ROLE_ADMIN") {
                navigate("/dashboard-prescritor");
            } else {
                setError("perfil de usuário não reconhecido no token");
            }

        } catch (err) {
            setError("falha de conexão com o servidor");
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
                    src="/images/logotipo-icon.svg"
                />
                <img
                    alt="Logotipo 2AG"
                    className="login__art__top-right"
                    src="/images/logotipo-icon.svg"
                />
                <img
                    alt="Logotipo 2AG"
                    className="login__art__center"
                    src="/images/logotipo-vertical.svg"
                />
                <img
                    alt="Logotipo 2AG"
                    className="login__art__bottom-left"
                    src="/images/logotipo-icon.svg"
                />
                <img
                    alt="Logotipo 2AG"
                    className="login__art__bottom-right"
                    src="/images/logotipo-icon.svg"
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