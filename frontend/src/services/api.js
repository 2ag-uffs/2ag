// cliente unico pra falar com a api
// o front e a api ficam na mesma origem entao todo caminho comeca com api
// a sessao fica num cookie httponly q o javascript nem enxerga
const BASE_URL = "/api";

// quem esta logado agora
// eh carregado quando o app abre e muda no login e no logout
let loggedUser = null;

export function getLoggedUser() {
    return loggedUser;
}

export function setLoggedUser(user) {
    loggedUser = user;
}

// erro da api com o status a mensagem e os erros de cada campo
export class ApiError extends Error {
    constructor(status, message, errors) {
        super(message || "Ocorreu um erro.");
        this.status = status;
        this.errors = errors || null;
    }

    // transforma a lista de erros num objeto de campo e mensagem q os formularios usam
    fieldErrors() {
        const errorsByField = {};
        if (!this.errors) {
            return errorsByField;
        }
        this.errors.forEach((fieldError) => {
            errorsByField[fieldError.field] = fieldError.message;
        });
        return errorsByField;
    }
}

async function readBody(response) {
    if (response.status === 204) {
        return null;
    }
    const text = await response.text();
    if (!text) {
        return null;
    }
    try {
        return JSON.parse(text);
    } catch {
        return null;
    }
}

async function request(path, options = {}) {
    const headers = {"Content-Type": "application/json", ...(options.headers || {})};
    const response = await fetch(BASE_URL + path, {...options, headers});
    const body = await readBody(response);

    // 401 com alguem logado quer dizer q a sessao acabou e a pessoa volta pro login
    // 401 sem ninguem logado eh senha errada e quem mostra a mensagem eh a tela
    if (response.status === 401) {
        const hadSession = loggedUser !== null;
        loggedUser = null;
        if (hadSession && !window.location.pathname.startsWith("/login")) {
            window.location.href = "/login";
        }
        const message = hadSession
            ? "Sua sessão expirou. Faça login novamente."
            : (body && body.message) || "E-mail ou senha inválidos.";
        throw new ApiError(401, message);
    }

    if (!response.ok) {
        throw new ApiError(response.status, body && body.message, body && body.errors);
    }

    return body;
}

// pergunta pra api quem esta logado quando o app abre
// sem sessao valida o resultado eh null
export async function loadSession() {
    try {
        loggedUser = await request("/auth/me");
    } catch {
        loggedUser = null;
    }
    return loggedUser;
}

export async function logout() {
    try {
        await request("/auth/logout", {method: "POST"});
    } catch {
        // mesmo se a api falhar a tela esquece quem estava logado
    } finally {
        loggedUser = null;
    }
}

export const apiService = {
    get(path) {
        return request(path);
    },
    post(path, body) {
        return request(path, {method: "POST", body: JSON.stringify(body)});
    },
    put(path, body) {
        return request(path, {method: "PUT", body: JSON.stringify(body)});
    },
    delete(path) {
        return request(path, {method: "DELETE"});
    },
};

export default apiService;
