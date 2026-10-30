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

// o layout escuta o aviso e mostra na hora o nome salvo no perfil
export function setLoggedUser(user) {
    loggedUser = user;
    window.dispatchEvent(new Event("logged-user-changed"));
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

// aviso padrao qnd a api nem chegou a responder
export const CONNECTION_ERROR_MESSAGE = "Não foi possível falar com o servidor. Confira sua internet e tente de novo.";

// mensagem da api ou o aviso de sem conexao
export function errorMessageOf(requestError, offlineText = CONNECTION_ERROR_MESSAGE) {
    if (requestError instanceof ApiError) {
        return requestError.message;
    }
    return offlineText;
}

// separa o erro da api em erros de campo e mensagem geral
export function readRequestError(requestError, offlineText = CONNECTION_ERROR_MESSAGE) {
    if (!(requestError instanceof ApiError)) {
        return {fieldErrors: {}, message: offlineText};
    }
    const errorsByField = requestError.fieldErrors();
    if (Object.keys(errorsByField).length > 0) {
        return {fieldErrors: errorsByField, message: null};
    }
    return {fieldErrors: {}, message: requestError.message};
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

const SESSION_EXPIRED_MESSAGE = "Sua sessão expirou. Faça login novamente.";

// o login avisa q a sessao acabou e depois volta pra tela onde a pessoa estava
function goToLoginWithExpiredSession() {
    const currentPath = window.location.pathname + window.location.search;
    window.location.href = "/entrar?sessao=expirada&voltar=" + encodeURIComponent(currentPath);
}

// o layout escuta o aviso e abre o termo por cima da tela aberta
function openPendingTerm(user) {
    loggedUser = user;
    window.dispatchEvent(new Event("term-pending"));
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
        if (hadSession && !window.location.pathname.startsWith("/entrar")) {
            goToLoginWithExpiredSession();
        }
        const message = hadSession
            ? SESSION_EXPIRED_MESSAGE
            : (body && body.message) || "E-mail ou senha inválidos.";
        throw new ApiError(401, message);
    }

    // o termo ficou pendente com a sessao aberta (versao nova ou aniversario de 18 anos)
    if (response.status === 403 && response.headers.get("X-Term-Pending") === "true" && loggedUser) {
        openPendingTerm({...loggedUser, termPending: true});
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

// a data de nascimento salva no perfil pode cruzar os 18 anos e deixar o termo pendente,
// entao pergunta de novo pra api e abre o termo na hora, sem esperar a proxima gravacao dar 403
// quem decide eh a api, q conta a idade no horario de brasilia
export async function checkPendingTerm() {
    try {
        const sessionUser = await apiService.get("/auth/me");
        if (sessionUser && sessionUser.termPending && loggedUser) {
            openPendingTerm(sessionUser);
        }
    } catch {
        // sem resposta o 403 da proxima gravacao ainda abre o termo
    }
}

// o nome do arquivo vem do cabecalho q a api manda
function fileNameOf(response, fallback) {
    const disposition = response.headers.get("Content-Disposition") || "";
    const match = disposition.match(/filename="?([^";]+)"?/);
    return match ? match[1] : fallback;
}

// um link temporario com o arquivo faz o navegador salvar ele
function saveFile(blob, fileName) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}

// baixa um arquivo pela api em vez de deixar o navegador navegar ate ele
// assim sessao vencida e erro viram mensagem na tela, e n um arquivo com o json do erro dentro
export async function download(path, fallbackName) {
    const response = await fetch(BASE_URL + path);
    if (!response.ok) {
        const body = await readBody(response);
        if (response.status === 401) {
            loggedUser = null;
            goToLoginWithExpiredSession();
            throw new ApiError(401, SESSION_EXPIRED_MESSAGE);
        }
        throw new ApiError(response.status, body && body.message, body && body.errors);
    }
    const blob = await response.blob();
    saveFile(blob, fileNameOf(response, fallbackName));
}

// o cookie da sessao eh httponly e so a api apaga ele
// entao a tela so esquece quem estava logado qnd a api confirma, senao a proxima pessoa no computador entraria logada
// 401 tbm conta como saiu pq a sessao ja tinha acabado
// devolve se saiu ou n
export async function logout() {
    try {
        const response = await fetch(BASE_URL + "/auth/logout", {method: "POST"});
        if (!response.ok && response.status !== 401) {
            return false;
        }
    } catch {
        return false;
    }
    loggedUser = null;
    return true;
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
