import {StrictMode} from "react";
import {createRoot} from "react-dom/client";
import AppRouter from "./app/router.jsx";
import StatusPage from "./components/status-page/status-page.jsx";
import {ApiError, errorMessageOf, loadSession} from "./services/api.js";
import "./styles/tokens.css";
import "./index.css";
import "./styles/aviso.css";
import "./styles/button.css";
import "./styles/colors.css";
import "./styles/fonts.css";
import "./styles/input.css";

const root = createRoot(document.getElementById("root"));

// descobre quem esta logado antes de montar as rotas
// as regras de acesso de cada rota dependem disso
// sem resposta da api a tela diz q o servidor n respondeu, e o tentar de novo recarrega a mesma tela
// o erro da propria api (o 500 dela ou o limite do nginx) mostra a mensagem dela
loadSession().then(
    () => {
        root.render(
            <StrictMode>
                <AppRouter/>
            </StrictMode>,
        );
    },
    (sessionError) => {
        const title = sessionError instanceof ApiError ? "Algo deu errado" : "Sem conexão com o servidor";
        root.render(
            <StrictMode>
                <StatusPage
                    title={title}
                    message={errorMessageOf(sessionError)}
                    showReloadButton={true}
                    showHomeLink={false}
                />
            </StrictMode>,
        );
    },
);
