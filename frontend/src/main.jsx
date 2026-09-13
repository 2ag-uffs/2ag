import {StrictMode} from "react";
import {createRoot} from "react-dom/client";
import "./index.css";
import Routes from "./routes/routes.jsx";
import {loadSession} from "./services/api.js";
import "./styles/aviso.css";
import "./styles/button.css";
import "./styles/colors.css";
import "./styles/fonts.css";
import "./styles/input.css";

// descobre quem esta logado antes de desenhar a primeira tela
loadSession().finally(() => {
    createRoot(document.getElementById("root")).render(
        <StrictMode>
            <Routes/>
        </StrictMode>,
    );
});
