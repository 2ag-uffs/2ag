import {redirect} from "react-router";
import {hasKnownRole, homePathFor} from "./role-home.js";
import {getLoggedUser, logout} from "../services/api.js";

// regras de acesso das rotas
// rodam antes da tela abrir entao quem n pode entrar nem chega a ver a tela
// a api confere tudo de novo pq esconder rota n protege dado nenhum

// so abre a rota quem esta logado e tem um dos papeis permitidos
// sem papel nenhum na lista basta estar logado
export function requireRole(...allowedRoles) {
    return ({request}) => {
        const loggedUser = getLoggedUser();
        if (!loggedUser) {
            // o link do e-mail aberto sem sessao volta pra mesma tela dps do login
            const url = new URL(request.url);
            return redirect("/entrar?voltar=" + encodeURIComponent(url.pathname + url.search));
        }
        if (allowedRoles.length > 0 && !allowedRoles.includes(loggedUser.role)) {
            return redirect(homePathFor(loggedUser));
        }
        return null;
    };
}

// quem ja esta logado n precisa ver o login nem o cadastro de novo
export async function redirectLoggedUserHome() {
    const loggedUser = getLoggedUser();
    if (!loggedUser) {
        return null;
    }
    // papel desconhecido teria o proprio login como tela inicial e ficaria em laco, entao sai e ve o login
    if (!hasKnownRole(loggedUser)) {
        await logout();
        return null;
    }
    return redirect(homePathFor(loggedUser));
}

// o cadastro abre com o link de convite mesmo com alguem logado
// a tela explica q precisa sair da conta aberta, em vez de o convite sumir sem aviso
export async function redirectLoggedUserHomeUnlessInvite({request}) {
    if (new URL(request.url).searchParams.get("convite")) {
        return null;
    }
    return redirectLoggedUserHome();
}

// o link de senha nova e o de primeiro acesso do prescritor tbm abrem com alguem logado
// a tela diz qual conta esta aberta e deixa sair e seguir com o mesmo link
export async function redirectLoggedUserHomeUnlessToken({request}) {
    if (new URL(request.url).searchParams.get("token")) {
        return null;
    }
    return redirectLoggedUserHome();
}

// o endereco raiz manda cada um pra sua tela inicial
export function redirectHome() {
    return redirect(homePathFor(getLoggedUser()));
}
