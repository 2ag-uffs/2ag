import {redirect} from "react-router";
import {homePathFor} from "./role-home.js";
import {getLoggedUser} from "../services/api.js";

// regras de acesso das rotas
// rodam antes da tela abrir entao quem n pode entrar nem chega a ver a tela
// a api confere tudo de novo pq esconder rota n protege dado nenhum

// so abre a rota quem esta logado e tem um dos papeis permitidos
// sem papel nenhum na lista basta estar logado
export function requireRole(...allowedRoles) {
    return () => {
        const loggedUser = getLoggedUser();
        if (!loggedUser) {
            return redirect("/login");
        }
        if (allowedRoles.length > 0 && !allowedRoles.includes(loggedUser.role)) {
            return redirect(homePathFor(loggedUser));
        }
        return null;
    };
}

// quem ja esta logado n precisa ver o login nem o cadastro de novo
export function redirectLoggedUserHome() {
    const loggedUser = getLoggedUser();
    if (loggedUser) {
        return redirect(homePathFor(loggedUser));
    }
    return null;
}

// o endereco raiz manda cada um pra sua tela inicial
export function redirectHome() {
    return redirect(homePathFor(getLoggedUser()));
}
