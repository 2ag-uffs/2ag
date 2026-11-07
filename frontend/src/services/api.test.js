import {afterEach, describe, expect, it, vi} from "vitest";
import {
    ApiError,
    apiService,
    checkPendingTerm,
    CONNECTION_ERROR_MESSAGE,
    download,
    errorMessageOf,
    getLoggedUser,
    leaveExpiredSession,
    loginAgain,
    logout,
    readRequestError,
    setLoggedUser,
    setLoginAgainOpener,
} from "./api.js";

const MARIA = {id: 3, name: "Maria Souza", email: "maria@email.com", role: "PATIENT", termPending: false};

// resposta da api como o fetch devolve, com o status e o json
function apiResponse(status, body) {
    return new Response(body === undefined ? null : JSON.stringify(body), {status});
}

// o corpo q o fetch recebeu numa chamada
function sentBody(fetchCall) {
    return JSON.parse(fetchCall[1].body);
}

// deixa as promessas q ja podem andar andarem, pra conferir q uma ainda esta esperando
function flushPromises() {
    return new Promise((resolve) => setTimeout(resolve, 0));
}

// o cookie da sessao eh httponly e so a api apaga ele
describe("logout", () => {
    afterEach(() => {
        setLoggedUser(null);
        vi.restoreAllMocks();
    });

    it("esquece quem estava logado qnd a api confirma q saiu", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: true, status: 204});
        setLoggedUser(MARIA);

        expect(await logout()).toBe(true);
        expect(getLoggedUser()).toBeNull();
        expect(fetchSpy).toHaveBeenCalledWith("/api/auth/logout", expect.objectContaining({method: "POST"}));
    });

    // se a tela fingisse q saiu a proxima pessoa no computador abriria o sistema logada
    it("com a api fora do ar a sessao continua e a tela n finge q saiu", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: false, status: 502});
        setLoggedUser(MARIA);

        expect(await logout()).toBe(false);
        expect(getLoggedUser()).toEqual(MARIA);
    });

    it("sem internet a sessao tbm continua", async () => {
        vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));
        setLoggedUser(MARIA);

        expect(await logout()).toBe(false);
        expect(getLoggedUser()).toEqual(MARIA);
    });

    // a sessao ja tinha acabado entao n sobra nada pra encerrar
    it("401 conta como saiu", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: false, status: 401});
        setLoggedUser(MARIA);

        expect(await logout()).toBe(true);
        expect(getLoggedUser()).toBeNull();
    });
});

// a data salva no perfil pode deixar o termo pendente, e o layout abre ele pelo aviso
describe("checkPendingTerm", () => {
    afterEach(() => {
        setLoggedUser(null);
        vi.restoreAllMocks();
    });

    it("abre o termo qnd a api diz q ele ficou pendente", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({...MARIA, termPending: true, termPendingByAge: true});
        const openTerm = vi.fn();
        window.addEventListener("term-pending", openTerm);
        setLoggedUser(MARIA);

        await checkPendingTerm();
        window.removeEventListener("term-pending", openTerm);

        expect(openTerm).toHaveBeenCalledTimes(1);
        expect(getLoggedUser()).toEqual({...MARIA, termPending: true, termPendingByAge: true});
    });

    // sem resposta da api o 403 da proxima gravacao ainda abre o termo
    it("sem termo pendente ou sem resposta da api nada abre", async () => {
        const get = vi.spyOn(apiService, "get").mockResolvedValue(MARIA);
        const openTerm = vi.fn();
        window.addEventListener("term-pending", openTerm);
        setLoggedUser(MARIA);

        await checkPendingTerm();
        get.mockRejectedValue(new TypeError("Failed to fetch"));
        await checkPendingTerm();
        window.removeEventListener("term-pending", openTerm);

        expect(openTerm).not.toHaveBeenCalled();
        expect(getLoggedUser()).toEqual(MARIA);
    });
});

describe("errorMessageOf", () => {
    it("erro da api mostra a mensagem q veio dela", () => {
        expect(errorMessageOf(new ApiError(409, "Horário já ocupado."))).toBe("Horário já ocupado.");
    });

    it("sem resposta da api vira o aviso de sem conexao", () => {
        expect(errorMessageOf(new TypeError("Failed to fetch"))).toBe(CONNECTION_ERROR_MESSAGE);
    });

    it("a tela pode trocar o aviso de sem conexao pelo dela", () => {
        expect(errorMessageOf(new TypeError("Failed to fetch"), "Não foi possível carregar o painel."))
            .toBe("Não foi possível carregar o painel.");
        expect(errorMessageOf(new ApiError(404, "Paciente não encontrado."), "Não foi possível carregar o painel."))
            .toBe("Paciente não encontrado.");
    });
});

describe("readRequestError", () => {
    it("erro de campo vai pros campos e fica sem mensagem geral", () => {
        const requestError = new ApiError(400, "Dados inválidos.", [{field: "email", message: "E-mail inválido"}]);

        expect(readRequestError(requestError)).toEqual({fieldErrors: {email: "E-mail inválido"}, message: null});
    });

    it("erro da api sem campo vira a mensagem geral", () => {
        expect(readRequestError(new ApiError(409, "Horário já ocupado.")))
            .toEqual({fieldErrors: {}, message: "Horário já ocupado."});
    });

    it("sem resposta da api vira o aviso de sem conexao ou o texto q a tela mandar", () => {
        expect(readRequestError(new TypeError("Failed to fetch")))
            .toEqual({fieldErrors: {}, message: CONNECTION_ERROR_MESSAGE});
        expect(readRequestError(new TypeError("Failed to fetch"), "Não foi possível falar com o servidor."))
            .toEqual({fieldErrors: {}, message: "Não foi possível falar com o servidor."});
    });
});

// a sessao venceu com uma tela aberta: o envio q deu 401 espera a pessoa entrar de novo por cima da tela
// e vai de novo com a sessao nova, em vez de a pagina recarregar e perder o q foi digitado (#151)
describe("sessao vencida com a tela aberta", () => {
    afterEach(() => {
        leaveExpiredSession();
        setLoginAgainOpener(null);
        setLoggedUser(null);
        vi.restoreAllMocks();
    });

    // 401 do envio, depois o login de novo e por fim o envio outra vez
    function mockExpiredSessionThenLogin(loginResponse) {
        return vi.spyOn(globalThis, "fetch").mockImplementation((url, options) => {
            if (url === "/api/auth/login") {
                return Promise.resolve(loginResponse);
            }
            const isFirstTry = globalThis.fetch.mock.calls.length === 1;
            if (isFirstTry) {
                return Promise.resolve(apiResponse(401, {message: "Sessão inválida"}));
            }
            return Promise.resolve(apiResponse(200, {saved: true, answer: sentBody([url, options]).answer}));
        });
    }

    it("o envio q deu 401 abre o login por cima e termina com a resposta de vdd dps de entrar de novo", async () => {
        const fetchSpy = mockExpiredSessionThenLogin(apiResponse(200, {...MARIA, termPendingByAge: false}));
        const openLoginAgain = vi.fn();
        setLoginAgainOpener(openLoginAgain);
        setLoggedUser(MARIA);

        const saving = apiService.post("/scales/answers", {answer: "dormi bem"});
        await flushPromises();

        expect(openLoginAgain).toHaveBeenCalledTimes(1);
        expect(fetchSpy).toHaveBeenCalledTimes(1);
        // a pessoa continua logada na tela enquanto o login por cima esta aberto
        expect(getLoggedUser()).toEqual(MARIA);

        await loginAgain("Senha#2026");

        expect(await saving).toEqual({saved: true, answer: "dormi bem"});
        expect(fetchSpy).toHaveBeenCalledTimes(3);
        // o e-mail vai travado na conta q estava aberta
        expect(fetchSpy.mock.calls[1][0]).toBe("/api/auth/login");
        expect(sentBody(fetchSpy.mock.calls[1])).toEqual({email: "maria@email.com", password: "Senha#2026"});
        expect(fetchSpy.mock.calls[2][0]).toBe("/api/scales/answers");
        expect(fetchSpy.mock.calls[2][1]).toEqual(expect.objectContaining({method: "POST"}));
        expect(getLoggedUser()).toEqual({...MARIA, termPendingByAge: false});
    });

    it("senha errada no login por cima vira mensagem e o envio continua esperando", async () => {
        const fetchSpy = mockExpiredSessionThenLogin(apiResponse(401, {message: "E-mail ou senha inválidos"}));
        setLoginAgainOpener(vi.fn());
        setLoggedUser(MARIA);
        let isSettled = false;
        const saving = apiService.post("/scales/answers", {answer: "dormi bem"}).finally(() => {
            isSettled = true;
        });

        await expect(loginAgain("senha-errada")).rejects.toMatchObject({status: 401, message: "E-mail ou senha inválidos"});
        await flushPromises();

        expect(isSettled).toBe(false);
        expect(getLoggedUser()).toEqual(MARIA);

        fetchSpy.mockImplementation((url) => {
            if (url === "/api/auth/login") {
                return Promise.resolve(apiResponse(200, MARIA));
            }
            return Promise.resolve(apiResponse(200, {saved: true}));
        });
        await loginAgain("Senha#2026");

        expect(await saving).toEqual({saved: true});
    });

    // n deveria acontecer com o e-mail travado, mas se a api devolver outra conta nada eh reenviado
    it("outra conta no login por cima n reenvia nada, sai dela e a pagina volta pro login como antes", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
            if (url === "/api/auth/login") {
                return Promise.resolve(apiResponse(200, {id: 9, name: "Outra Pessoa", email: "outra@email.com", role: "PATIENT"}));
            }
            if (url === "/api/auth/logout") {
                return Promise.resolve(apiResponse(204));
            }
            return Promise.resolve(apiResponse(401, {message: "Sessão inválida"}));
        });
        setLoginAgainOpener(vi.fn());
        setLoggedUser(MARIA);
        const saving = apiService.post("/scales/answers", {answer: "dormi bem"});

        await loginAgain("Senha#2026");

        await expect(saving).rejects.toMatchObject({status: 401, message: "Sua sessão expirou. Faça login novamente."});
        expect(getLoggedUser()).toBeNull();
        const calledPaths = fetchSpy.mock.calls.map((call) => call[0]);
        expect(calledPaths).toEqual(["/api/scales/answers", "/api/auth/login", "/api/auth/logout"]);
    });

    it("sair no login por cima derruba o envio q esperava e esquece quem estava logado", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch")
            .mockResolvedValue(apiResponse(401, {message: "Sessão inválida"}));
        setLoginAgainOpener(vi.fn());
        setLoggedUser(MARIA);
        const saving = apiService.put("/anamnesis/7", {smokes: null});
        await flushPromises();

        leaveExpiredSession();

        await expect(saving).rejects.toMatchObject({status: 401, message: "Sua sessão expirou. Faça login novamente."});
        expect(getLoggedUser()).toBeNull();
        expect(fetchSpy).toHaveBeenCalledTimes(1);
    });

    // exportar o csv com a sessao vencida tbm espera o login por cima e baixa o arquivo dps (CT-35)
    it("o download q deu 401 espera o login por cima e baixa o arquivo dps de entrar de novo", async () => {
        let isSessionExpired = true;
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
            if (url === "/api/auth/login") {
                isSessionExpired = false;
                return Promise.resolve(apiResponse(200, MARIA));
            }
            if (isSessionExpired) {
                return Promise.resolve(apiResponse(401, {message: "Sessão inválida"}));
            }
            return Promise.resolve(new Response("data;nota", {
                status: 200, headers: {"Content-Disposition": "attachment; filename=\"progresso-3.csv\""},
            }));
        });
        // o jsdom n cria link de arquivo nem baixa nada, entao o teste so olha o link q seria clicado
        URL.createObjectURL = vi.fn(() => "blob:arquivo");
        URL.revokeObjectURL = vi.fn();
        const savedNames = [];
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function () {
            savedNames.push(this.download);
        });
        const openLoginAgain = vi.fn();
        setLoginAgainOpener(openLoginAgain);
        setLoggedUser(MARIA);

        const saving = download("/patients/3/export/progress.csv", "progresso.csv");
        await flushPromises();

        expect(openLoginAgain).toHaveBeenCalledTimes(1);
        expect(savedNames).toEqual([]);

        await loginAgain("Senha#2026");
        await saving;

        expect(fetchSpy.mock.calls.map((call) => call[0])).toEqual([
            "/api/patients/3/export/progress.csv", "/api/auth/login", "/api/patients/3/export/progress.csv",
        ]);
        expect(savedNames).toEqual(["progresso-3.csv"]);
    });

    it("sair no login por cima derruba o download q esperava", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch")
            .mockResolvedValue(apiResponse(401, {message: "Sessão inválida"}));
        setLoginAgainOpener(vi.fn());
        setLoggedUser(MARIA);
        const saving = download("/patients/3/export/progress.csv", "progresso.csv");
        await flushPromises();

        leaveExpiredSession();

        await expect(saving).rejects.toMatchObject({status: 401, message: "Sua sessão expirou. Faça login novamente."});
        expect(fetchSpy).toHaveBeenCalledTimes(1);
    });

    // fora do layout (link de senha nova aberto com alguem logado) n tem onde abrir o login por cima
    it("sem o layout montado o 401 com alguem logado recarrega no login como sempre", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        vi.spyOn(globalThis, "fetch").mockResolvedValue(apiResponse(401, {message: "Sessão inválida"}));
        setLoggedUser(MARIA);

        await expect(apiService.get("/profile")).rejects.toMatchObject({status: 401, message: "Sua sessão expirou. Faça login novamente."});
        expect(getLoggedUser()).toBeNull();
    });

    it("na tela de login o 401 continua sendo senha errada, sem login por cima", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(apiResponse(401, {message: "E-mail ou senha inválidos"}));
        const openLoginAgain = vi.fn();
        setLoginAgainOpener(openLoginAgain);

        await expect(apiService.post("/auth/login", {email: "maria@email.com", password: "x"}))
            .rejects.toMatchObject({status: 401, message: "E-mail ou senha inválidos"});
        expect(openLoginAgain).not.toHaveBeenCalled();
    });
});
