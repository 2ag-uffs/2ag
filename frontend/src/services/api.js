// cliente unico pra falar com a api
// o front e a api ficam na mesma origem entao todo caminho comeca com api
const BASE_URL = "/api";

const TOKEN_KEY = "authToken";

export function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

export function saveToken(token) {
    localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
    localStorage.removeItem(TOKEN_KEY);
}

// le o que veio do jwt sem validar assinatura. serve so pra tela saber
// o id e o papel de quem esta logado, quem valida de verdade eh a api
export function getLoggedUser() {
    const token = getToken();
    if (!token) {
        return null;
    }
    try {
        return JSON.parse(atob(token.split(".")[1]));
    } catch {
        return null;
    }
}

// erro da api com o que a tela precisa: o status, a mensagem geral e a
// lista de erros por campo que o backend manda quando a validacao falha
export class ApiError extends Error {
    constructor(status, message, errors) {
        super(message || "Ocorreu um erro.");
        this.status = status;
        this.errors = errors || null;
    }

    // transforma [{field, message}] em {campo: mensagem}, que eh o
    // formato que os formularios usam
    fieldErrors() {
        if (!this.errors) {
            return {};
        }
        return this.errors.reduce((acc, erro) => {
            acc[erro.field] = erro.message;
            return acc;
        }, {});
    }
}

async function lerCorpo(response) {
    if (response.status === 204) {
        return null;
    }
    const texto = await response.text();
    if (!texto) {
        return null;
    }
    try {
        return JSON.parse(texto);
    } catch {
        return null;
    }
}

async function request(path, options = {}) {
    const headers = {"Content-Type": "application/json", ...(options.headers || {})};

    const token = getToken();
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(`${BASE_URL}${path}`, {...options, headers});
    const corpo = await lerCorpo(response);

    // 401 com token na mao quer dizer que a sessao acabou, ai manda pro
    // login. 401 sem token eh so credencial errada, tipo no proprio
    // login, e quem mostra a mensagem eh a tela.
    // 403 eh outra coisa: esta logado mas aquilo n eh dele
    if (response.status === 401) {
        const tinhaSessao = Boolean(token);
        clearToken();
        if (tinhaSessao && !window.location.pathname.startsWith("/login")) {
            window.location.href = "/login";
        }
        const mensagem = tinhaSessao
            ? "Sua sessão expirou. Faça login novamente."
            : (corpo && corpo.message) || "E-mail ou senha inválidos.";
        throw new ApiError(401, mensagem);
    }

    if (!response.ok) {
        throw new ApiError(response.status, corpo && corpo.message, corpo && corpo.errors);
    }

    return corpo;
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
