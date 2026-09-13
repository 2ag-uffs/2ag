import {StrictMode} from "react";
import {createRoot} from "react-dom/client";
import AppRouter from "./app/router.jsx";
import {loadSession} from "./services/api.js";
import "./index.css";
import "./styles/aviso.css";
import "./styles/button.css";
import "./styles/colors.css";
import "./styles/fonts.css";
import "./styles/input.css";

// descobre quem esta logado antes de montar as rotas
// as regras de acesso de cada rota dependem disso
loadSession().finally(() => {
    createRoot(document.getElementById("root")).render(
        <StrictMode>
            <AppRouter/>
        </StrictMode>,
    );
});
