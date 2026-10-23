import {afterEach, describe, expect, it, vi} from "vitest";
import {getLoggedUser, logout, setLoggedUser} from "./api.js";

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
