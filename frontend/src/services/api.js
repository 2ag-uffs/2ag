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
        super(message || "Houve um problema no sistema. Tente de novo em alguns minutos e, se continuar, fale com a clínica.");
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
export const CONNECTION_ERROR_MESSAGE = "Não foi possível conectar ao sistema. Confira sua internet e tente de novo.";

// mensagem da api ou o aviso de sem conexao
export function errorMessageOf(requestError, offlineText = CONNECTION_ERROR_MESSAGE) {
    if (requestError instanceof ApiError) {
        return requestError.message;
    }
    return offlineText;
}

// erro de carregar uma tela, pro cartao de erro (components/error-card): a frase e se foi falta de acesso
// o 403 quer dizer q a conta n pode abrir aquilo, e o cartao troca o Tentar de novo pelo Ir para o inicio
export function loadErrorOf(requestError, offlineText = CONNECTION_ERROR_MESSAGE) {
    return {
        message: errorMessageOf(requestError, offlineText),
        isDenied: requestError instanceof ApiError && requestError.status === 403,
    };
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

// erro 5xx sem o json da api eh o proxy (nginx ou vite) dizendo q a api esta fora do ar
// a tela trata igual a falta de internet, com o aviso de sem conexao em vez do aviso de problema no sistema
function isApiDown(response, body) {
    return response.status >= 500 && body === null;
}

// a msm frase q a api manda qnd a sessao acabou (SecurityErrorHandler)
const SESSION_EXPIRED_MESSAGE = "Sua sessão expirou ou foi encerrada. Entre de novo para continuar.";
// a msm frase q a api manda pro e-mail ou senha errados, qnd a resposta vem sem ela
const WRONG_LOGIN_MESSAGE = "E-mail ou senha incorretos. Confira os dois e tente de novo.";

// o login avisa q a sessao acabou e depois volta pra tela onde a pessoa estava
function goToLoginWithExpiredSession() {
    const currentPath = window.location.pathname + window.location.search;
    window.location.href = "/entrar?sessao=expirada&voltar=" + encodeURIComponent(currentPath);
}

// a sessao venceu com uma tela aberta (#151)
// o layout se cadastra aqui qnd monta pra abrir o login por cima da tela, e o envio q deu 401 fica
// esperando a pessoa entrar de novo. fica so na memoria: nada do q foi digitado vai pro navegador,
// q pode ser de mais gente
let loginAgainOpener = null;
let pendingRequests = [];

export function setLoginAgainOpener(opener) {
    loginAgainOpener = opener;
}

// tem envio parado esperando a pessoa entrar de novo?
// o registro da consulta, a prescricao e o mini-exame perguntam antes de sair enquanto tiver
// (decisao de novembro/2026). quem quer saber na hora escuta o aviso waiting-login-changed
export function hasSendWaitingLogin() {
    return pendingRequests.length > 0;
}

function setPendingRequests(requests) {
    pendingRequests = requests;
    window.dispatchEvent(new Event("waiting-login-changed"));
}

// guarda o envio e abre o login por cima. a promessa so termina qnd a pessoa entra de novo ou sai
function waitForLoginAgain(retry, deactivatedMessage) {
    return new Promise((resolve, reject) => {
        setPendingRequests([...pendingRequests, {retry, resolve, reject}]);
        loginAgainOpener(deactivatedMessage);
    });
}

// a sessao caiu pq a conta foi desativada: a api avisa no cabecalho e a mensagem dela eh o motivo (CT-41)
function deactivatedMessageOf(response, body) {
    if (response.headers.get("X-Account-Deactivated") === "true" && body && body.message) {
        return body.message;
    }
    return null;
}

// sem o layout montado (link de senha nova aberto com alguem logado) n tem onde abrir o login por cima,
// entao a pagina recarrega no login como sempre
function handleExpiredSession(retry, deactivatedMessage = null) {
    if (loginAgainOpener) {
        return waitForLoginAgain(retry, deactivatedMessage);
    }
    loggedUser = null;
    goToLoginWithExpiredSession();
    throw new ApiError(401, SESSION_EXPIRED_MESSAGE);
}

// cada envio q estava esperando vai de novo com a sessao nova e a tela dele recebe a resposta de vdd
function retryPendingRequests() {
    const requests = pendingRequests;
    setPendingRequests([]);
    requests.forEach((pending) => pending.retry().then(pending.resolve, pending.reject));
}

// sair no login por cima: esquece quem estava logado e o q esperava falha como sessao vencida
export function leaveExpiredSession() {
    const requests = pendingRequests;
    loggedUser = null;
    setPendingRequests([]);
    requests.forEach((pending) => pending.reject(new ApiError(401, SESSION_EXPIRED_MESSAGE)));
}

// entra de novo por cima da tela, so na msm conta, e reenvia o q ficou esperando
// vai direto no fetch pq a senha errada aqui eh 401 e n pode cair na regra de sessao vencida do request
export async function loginAgain(password) {
    const response = await fetch(BASE_URL + "/auth/login", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({email: loggedUser.email, password}),
    });
    const body = await readBody(response);
    if (isApiDown(response, body)) {
        throw new Error(CONNECTION_ERROR_MESSAGE);
    }
    if (!response.ok) {
        const message = (body && body.message) || WRONG_LOGIN_MESSAGE;
        throw new ApiError(response.status, message, body && body.errors);
    }
    // outra conta no lugar da q estava aberta (n deveria acontecer com o e-mail travado):
    // nada eh reenviado, a conta nova sai e a pagina volta pro login como antes
    if (body.id !== loggedUser.id) {
        await logout();
        leaveExpiredSession();
        goToLoginWithExpiredSession();
        return;
    }
    setLoggedUser(body);
    retryPendingRequests();
}

// chave do envio de um formulario (decisao de novembro/2026)
// a resposta do salvar pode se perder com o registro ja gravado, e a pessoa clica de novo com tudo preenchido
// o formulario sorteia uma chave qnd abre e manda a mesma em todo envio dele: a api reconhece o envio
// repetido (mesma chave e mesmo conteudo, por alguns minutos) e devolve o registro q ja gravou
// formulario aberto de novo sorteia outra, entao a receita emitida de novo de proposito continua saindo
// n eh segredo nem senha, so precisa n repetir: a hora e um sorteio simples bastam
export function newRequestKey() {
    return Date.now().toString(36) + "-" + Math.random().toString(36).slice(2);
}

// o layout escuta o aviso e abre o termo por cima da tela aberta
function openPendingTerm(user) {
    loggedUser = user;
    window.dispatchEvent(new Event("term-pending"));
}

// o cookie eh um so pra todas as abas, e outra aba pode ter saido e entrado em outra conta
// (responsavel com dois filhos). cada envio diz de qual conta eh a tela aberta e, se o cookie for de outra,
// a api responde 401 e o login por cima abre travado nessa conta: nada vai pra ficha da outra
// o login e o cadastro n mandam pq eles q trocam a conta
function sessionUserHeader(path) {
    if (loggedUser === null || path === "/auth/login" || path === "/auth/register") {
        return {};
    }
    return {"X-Session-User": String(loggedUser.id)};
}

async function request(path, options = {}) {
    const headers = {"Content-Type": "application/json", ...sessionUserHeader(path), ...(options.headers || {})};
    const response = await fetch(BASE_URL + path, {...options, headers});
    const body = await readBody(response);

    // 401 com alguem logado quer dizer q a sessao acabou
    // 401 sem ninguem logado eh senha errada e quem mostra a mensagem eh a tela
    if (response.status === 401) {
        if (loggedUser === null) {
            throw new ApiError(401, (body && body.message) || WRONG_LOGIN_MESSAGE);
        }
        // na tela de login a sessao velha eh so esquecida
        if (window.location.pathname.startsWith("/entrar")) {
            loggedUser = null;
            throw new ApiError(401, SESSION_EXPIRED_MESSAGE);
        }
        return handleExpiredSession(() => request(path, options), deactivatedMessageOf(response, body));
    }

    // o termo ficou pendente com a sessao aberta (versao nova ou aniversario de 18 anos)
    if (response.status === 403 && response.headers.get("X-Term-Pending") === "true" && loggedUser) {
        openPendingTerm({...loggedUser, termPending: true});
    }

    if (isApiDown(response, body)) {
        throw new Error(CONNECTION_ERROR_MESSAGE);
    }
    if (!response.ok) {
        throw new ApiError(response.status, body && body.message, body && body.errors);
    }

    return body;
}

// pergunta pra api quem esta logado quando o app abre
// sem sessao valida (o 401 da api) o resultado eh null
// qualquer outro erro sobe, sem resposta ou com o 500 da api: ai n da pra saber se a pessoa esta logada,
// e mandar pro login faria ela achar q saiu da conta com o cookie ainda valendo
export async function loadSession() {
    try {
        loggedUser = await request("/auth/me");
    } catch (requestError) {
        loggedUser = null;
        const noSession = requestError instanceof ApiError && requestError.status === 401;
        if (!noSession) {
            throw requestError;
        }
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
    const response = await fetch(BASE_URL + path, {headers: sessionUserHeader(path)});
    if (!response.ok) {
        const body = await readBody(response);
        if (response.status === 401) {
            return handleExpiredSession(() => download(path, fallbackName), deactivatedMessageOf(response, body));
        }
        if (isApiDown(response, body)) {
            throw new Error(CONNECTION_ERROR_MESSAGE);
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
    // a chave do envio vai so nos formularios q criam registro clinico (veja newRequestKey)
    post(path, body, requestKey) {
        const headers = requestKey ? {"X-Request-Key": requestKey} : {};
        return request(path, {method: "POST", body: JSON.stringify(body), headers});
    },
    put(path, body) {
        return request(path, {method: "PUT", body: JSON.stringify(body)});
    },
    delete(path) {
        return request(path, {method: "DELETE"});
    },
};

export default apiService;
