import {StrictMode} from "react";
import {createRoot} from "react-dom/client";
import AppRouter from "./app/app-router.jsx";
import {preloadRouteOf} from "./app/router.jsx";
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

// a pagina guardada pelo navegador volta sem passar pela sessao
// dps de Sair o voltar devolvia o perfil inteiro, com cpf e endereco, num celular q pode ser da familia toda
window.addEventListener("pageshow", (event) => {
    if (event.persisted) {
        window.location.reload();
    }
});

// o codigo da tela aberta pelo link do e-mail n espera a resposta da sessao pra comecar a baixar
preloadRouteOf(window.location.pathname);

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
        const title = sessionError instanceof ApiError ? "Algo deu errado" : "Sem conexão com o sistema";
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
