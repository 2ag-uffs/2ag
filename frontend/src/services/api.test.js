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
    loadSession,
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
        const emailMessage = "Confira o e-mail: ele precisa ter um @ e o endereço depois dele, como nome@exemplo.com";
        const requestError = new ApiError(400, "Dados inválidos.", [{field: "email", message: emailMessage}]);

        expect(readRequestError(requestError)).toEqual({fieldErrors: {email: emailMessage}, message: null});
    });

    it("erro da api sem campo vira a mensagem geral", () => {
        expect(readRequestError(new ApiError(409, "Horário já ocupado.")))
            .toEqual({fieldErrors: {}, message: "Horário já ocupado."});
    });

    it("sem resposta da api vira o aviso de sem conexao ou o texto q a tela mandar", () => {
        expect(readRequestError(new TypeError("Failed to fetch")))
            .toEqual({fieldErrors: {}, message: CONNECTION_ERROR_MESSAGE});
        expect(readRequestError(new TypeError("Failed to fetch"), "Não foi possível carregar a lista. Confira sua internet e tente de novo."))
            .toEqual({fieldErrors: {}, message: "Não foi possível carregar a lista. Confira sua internet e tente de novo."});
    });
});

// o app pergunta quem esta logado qnd abre
describe("loadSession", () => {
    afterEach(() => {
        setLoggedUser(null);
        vi.restoreAllMocks();
    });

    it("com a sessao valendo devolve quem esta logado", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(apiResponse(200, MARIA));

        expect(await loadSession()).toEqual(MARIA);
        expect(getLoggedUser()).toEqual(MARIA);
    });

    it("sem sessao a api responde 401 e ninguem esta logado", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));

        expect(await loadSession()).toBeNull();
        expect(getLoggedUser()).toBeNull();
    });

    // sem resposta da api n da pra saber se a pessoa esta logada, entao o erro sobe pro app avisar (CT-40)
    it("com a api fora do ar ou sem internet o erro sobe em vez de dizer q ninguem esta logado", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("<html>502</html>", {status: 502}));
        await expect(loadSession()).rejects.toThrow(CONNECTION_ERROR_MESSAGE);
        expect(getLoggedUser()).toBeNull();

        fetchSpy.mockRejectedValue(new TypeError("Failed to fetch"));
        await expect(loadSession()).rejects.toThrow("Failed to fetch");
    });

    // so o 401 quer dizer q ninguem esta logado, o 500 da api e o 429 do nginx n dizem nada da sessao
    it("o erro 500 da api e o limite do nginx sobem com a mensagem deles em vez de dizer q ninguem esta logado", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch")
            .mockResolvedValue(apiResponse(500, {message: "Houve um problema no sistema. Tente de novo em alguns minutos e, se continuar, fale com a clínica"}));
        await expect(loadSession()).rejects.toThrow("Houve um problema no sistema. Tente de novo em alguns minutos e, se continuar, fale com a clínica");
        expect(getLoggedUser()).toBeNull();

        fetchSpy.mockResolvedValue(apiResponse(429, {message: "Muitas requisições em pouco tempo. Aguarde um instante e tente de novo."}));
        await expect(loadSession()).rejects.toThrow("Muitas requisições em pouco tempo. Aguarde um instante e tente de novo.");
    });
});

// com a api parada o proxy (nginx ou vite) responde 502, 503 ou 504 sem o json da api (CT-40)
describe("api fora do ar atras do proxy", () => {
    afterEach(() => {
        leaveExpiredSession();
        setLoginAgainOpener(null);
        setLoggedUser(null);
        vi.restoreAllMocks();
    });

    function proxyResponse(status) {
        return new Response("<html><body><h1>" + status + " Bad Gateway</h1></body></html>", {
            status, headers: {"Content-Type": "text/html"},
        });
    }

    it("o 502 do proxy vira o aviso de sem conexao, igual a falta de internet", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(proxyResponse(502));
        setLoggedUser(MARIA);

        const requestError = await apiService.get("/appointments/mine").catch((error) => error);

        expect(requestError).not.toBeInstanceOf(ApiError);
        expect(errorMessageOf(requestError)).toBe(CONNECTION_ERROR_MESSAGE);
        // a tela q tem o aviso dela de sem conexao mostra o dela
        expect(errorMessageOf(requestError, "Não foi possível carregar suas consultas."))
            .toBe("Não foi possível carregar suas consultas.");
        expect(readRequestError(requestError)).toEqual({fieldErrors: {}, message: CONNECTION_ERROR_MESSAGE});
    });

    it("503 e 504 do proxy e o 502 sem corpo do vite tbm", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch");
        for (const response of [proxyResponse(503), proxyResponse(504), new Response(null, {status: 502})]) {
            fetchSpy.mockResolvedValueOnce(response);
            const requestError = await apiService.post("/scales/acompanhamento-semanal/responses", {})
                .catch((error) => error);
            expect(errorMessageOf(requestError)).toBe(CONNECTION_ERROR_MESSAGE);
        }
    });

    it("entrar com a api fora diz q n falou com o servidor", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(proxyResponse(502));

        const requestError = await apiService.post("/auth/login", {email: "maria@email.com", password: "x"})
            .catch((error) => error);

        expect(errorMessageOf(requestError)).toBe(CONNECTION_ERROR_MESSAGE);
    });

    // no login por cima o erro sem json virava senha errada
    it("o login por cima com a api fora diz q n falou com o servidor e n q a senha esta errada", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(proxyResponse(502));
        setLoggedUser(MARIA);

        const requestError = await loginAgain("Senha#2026").catch((error) => error);

        expect(errorMessageOf(requestError)).toBe(CONNECTION_ERROR_MESSAGE);
        expect(requestError.status).toBeUndefined();
    });

    it("baixar o arquivo com a api fora diz q n falou com o servidor", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(proxyResponse(502));
        setLoggedUser(MARIA);

        const requestError = await download("/patients/3/export/progress.csv", "progresso.csv").catch((error) => error);

        expect(errorMessageOf(requestError)).toBe(CONNECTION_ERROR_MESSAGE);
    });

    // o erro inesperado da propria api vem com o json dela e a mensagem continua a dela
    it("o 500 com o json da api continua com a mensagem da api", async () => {
        vi.spyOn(globalThis, "fetch")
            .mockResolvedValue(apiResponse(500, {message: "Houve um problema no sistema. Tente de novo em alguns minutos e, se continuar, fale com a clínica"}));
        setLoggedUser(MARIA);

        const requestError = await apiService.get("/appointments/mine").catch((error) => error);

        expect(requestError).toBeInstanceOf(ApiError);
        expect(errorMessageOf(requestError)).toBe("Houve um problema no sistema. Tente de novo em alguns minutos e, se continuar, fale com a clínica");
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
                return Promise.resolve(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
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
        const fetchSpy = mockExpiredSessionThenLogin(apiResponse(401, {message: "E-mail ou senha incorretos. Confira os dois e tente de novo"}));
        setLoginAgainOpener(vi.fn());
        setLoggedUser(MARIA);
        let isSettled = false;
        const saving = apiService.post("/scales/answers", {answer: "dormi bem"}).finally(() => {
            isSettled = true;
        });

        await expect(loginAgain("senha-errada")).rejects.toMatchObject({status: 401, message: "E-mail ou senha incorretos. Confira os dois e tente de novo"});
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
            return Promise.resolve(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
        });
        setLoginAgainOpener(vi.fn());
        setLoggedUser(MARIA);
        const saving = apiService.post("/scales/answers", {answer: "dormi bem"});

        await loginAgain("Senha#2026");

        await expect(saving).rejects.toMatchObject({status: 401, message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar."});
        expect(getLoggedUser()).toBeNull();
        const calledPaths = fetchSpy.mock.calls.map((call) => call[0]);
        expect(calledPaths).toEqual(["/api/scales/answers", "/api/auth/login", "/api/auth/logout"]);
    });

    it("sair no login por cima derruba o envio q esperava e esquece quem estava logado", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch")
            .mockResolvedValue(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
        setLoginAgainOpener(vi.fn());
        setLoggedUser(MARIA);
        const saving = apiService.put("/anamnesis/7", {smokes: null});
        await flushPromises();

        leaveExpiredSession();

        await expect(saving).rejects.toMatchObject({status: 401, message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar."});
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
                return Promise.resolve(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
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
            .mockResolvedValue(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
        setLoginAgainOpener(vi.fn());
        setLoggedUser(MARIA);
        const saving = download("/patients/3/export/progress.csv", "progresso.csv");
        await flushPromises();

        leaveExpiredSession();

        await expect(saving).rejects.toMatchObject({status: 401, message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar."});
        expect(fetchSpy).toHaveBeenCalledTimes(1);
    });

    // fora do layout (link de senha nova aberto com alguem logado) n tem onde abrir o login por cima
    it("sem o layout montado o 401 com alguem logado recarrega no login como sempre", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        vi.spyOn(globalThis, "fetch").mockResolvedValue(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
        setLoggedUser(MARIA);

        await expect(apiService.get("/profile")).rejects.toMatchObject({status: 401, message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar."});
        expect(getLoggedUser()).toBeNull();
    });

    it("na tela de login o 401 continua sendo senha errada, sem login por cima", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(apiResponse(401, {message: "E-mail ou senha incorretos. Confira os dois e tente de novo"}));
        const openLoginAgain = vi.fn();
        setLoginAgainOpener(openLoginAgain);

        await expect(apiService.post("/auth/login", {email: "maria@email.com", password: "x"}))
            .rejects.toMatchObject({status: 401, message: "E-mail ou senha incorretos. Confira os dois e tente de novo"});
        expect(openLoginAgain).not.toHaveBeenCalled();
    });
});

// responsavel com dois filhos (#152): a aba 2 sai da conta de A e entra na de B com a tela de A aberta na aba 1
describe("conta trocada em outra aba", () => {
    afterEach(() => {
        leaveExpiredSession();
        setLoginAgainOpener(null);
        setLoggedUser(null);
        vi.restoreAllMocks();
    });

    const CHILD_A = {id: 11, name: "Filho A", email: "filho-a@email.com", role: "PATIENT", termPending: false};

    // o cookie eh um so pra todas as abas e so a api sabe de quem ele eh
    function mockSharedCookie(state) {
        return vi.spyOn(globalThis, "fetch").mockImplementation((url, options = {}) => {
            if (url === "/api/auth/me") {
                return Promise.resolve(apiResponse(200, {...CHILD_A, id: state.cookieOwner}));
            }
            if (url === "/api/auth/login") {
                state.cookieOwner = CHILD_A.id;
                return Promise.resolve(apiResponse(200, CHILD_A));
            }
            const sentUser = new Headers(options.headers).get("X-Session-User");
            if (sentUser !== null && sentUser !== String(state.cookieOwner)) {
                return Promise.resolve(apiResponse(401, {message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}));
            }
            state.savedFor.push(state.cookieOwner);
            return Promise.resolve(apiResponse(201, {id: 1, patientId: state.cookieOwner}));
        });
    }

    it("a tela aberta pra uma conta n grava nada com a sessao de outra", async () => {
        const state = {cookieOwner: 11, savedFor: []};
        mockSharedCookie(state);
        const openLoginAgain = vi.fn();
        setLoginAgainOpener(openLoginAgain);
        await loadSession();

        state.cookieOwner = 12;
        const saving = apiService.post("/scales/acompanhamento-semanal/responses", {answers: {dor: 3}});
        await flushPromises();

        // a resposta digitada pra A n pode ficar na ficha de B
        expect(state.savedFor).toEqual([]);
        expect(getLoggedUser()).toEqual(CHILD_A);
        // o login por cima abre travado na conta de A e o envio espera
        expect(openLoginAgain).toHaveBeenCalledTimes(1);

        await loginAgain("Senha#2026");

        expect(await saving).toEqual({id: 1, patientId: 11});
        expect(state.savedFor).toEqual([11]);
    });

    it("o envio e o download dizem de qual conta eh a tela, e o login e o cadastro n", async () => {
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation(() => Promise.resolve(apiResponse(200, {})));
        URL.createObjectURL = vi.fn(() => "blob:arquivo");
        URL.revokeObjectURL = vi.fn();
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
        setLoggedUser(CHILD_A);

        await apiService.put("/profile", {name: "Filho A"});
        await download("/patients/11/export/progress.csv", "progresso.csv");
        await apiService.post("/auth/login", {email: "filho-a@email.com", password: "x"});
        await apiService.post("/auth/register", {email: "filho-b@email.com"});

        const sentUsers = fetchSpy.mock.calls.map((call) => new Headers(call[1] && call[1].headers).get("X-Session-User"));
        expect(sentUsers).toEqual(["11", "11", null, null]);
    });
});
