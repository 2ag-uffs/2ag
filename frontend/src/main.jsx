import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import Routes from "./routes/routes.jsx";
import "./styles/button.css";
import "./styles/colors.css";
import "./styles/fonts.css";
import "./styles/input.css";

createRoot(document.getElementById("root")).render(
    <StrictMode>
        <Routes />
    </StrictMode>,
);