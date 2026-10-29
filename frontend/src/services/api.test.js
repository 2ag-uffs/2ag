import {afterEach, describe, expect, it, vi} from "vitest";
import {
    ApiError,
    CONNECTION_ERROR_MESSAGE,
    errorMessageOf,
    getLoggedUser,
    logout,
    readRequestError,
    setLoggedUser,
} from "./api.js";

const MARIA = {id: 3, name: "Maria Souza", role: "PATIENT", termPending: false};

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
