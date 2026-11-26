import {screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {CONNECTION_ERROR_MESSAGE} from "./services/api.js";
import {readCss} from "./test/css-rules.js";

// o main.jsx pergunta pra api quem esta logado e so dps monta as telas
// cada teste importa ele de novo numa pagina limpa, como um F5
async function openAppAt(path) {
    window.history.replaceState(null, "", path);
    document.body.innerHTML = "<div id=\"root\"></div>";
    vi.resetModules();
    await import("./main.jsx");
}

describe("abertura do app", () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        document.body.innerHTML = "";
        window.history.replaceState(null, "", "/");
    });

    // com a api fora a pessoa caia no login sem aviso e achava q tinha saido da conta, com o cookie valendo (CT-40)
    it("recarregar com a api fora do ar diz q o servidor n respondeu em vez de abrir o login", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("<html>502 Bad Gateway</html>", {status: 502}));

        await openAppAt("/agendamento-consulta");

        expect(await screen.findByText(CONNECTION_ERROR_MESSAGE)).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "Sem conexão com o sistema"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Tentar de novo"})).toBeInTheDocument();
        // o tentar de novo recarrega a mesma tela
        expect(window.location.pathname).toBe("/agendamento-consulta");
        expect(screen.queryByLabelText("Senha")).not.toBeInTheDocument();
    });

    it("sem internet tbm", async () => {
        vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));

        await openAppAt("/painel-paciente");

        expect(await screen.findByText(CONNECTION_ERROR_MESSAGE)).toBeInTheDocument();
        expect(window.location.pathname).toBe("/painel-paciente");
    });

    // o erro da propria api (o 500 dela ou o limite do nginx) tbm n quer dizer q a pessoa saiu da conta
    it("recarregar com o erro 500 da api mostra a mensagem dela em vez de abrir o login", async () => {
        vi.spyOn(globalThis, "fetch")
            .mockResolvedValue(new Response(JSON.stringify({message: "Houve um problema no sistema. Tente de novo em alguns minutos e, se continuar, fale com a clínica"}), {status: 500}));

        await openAppAt("/agendamento-consulta");

        expect(await screen.findByText("Houve um problema no sistema. Tente de novo em alguns minutos e, se continuar, fale com a clínica")).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "Algo deu errado"})).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Tentar de novo"})).toBeInTheDocument();
        expect(window.location.pathname).toBe("/agendamento-consulta");
        expect(screen.queryByLabelText("Senha")).not.toBeInTheDocument();
    });

    // perfil aberto pelo endereco, outro endereco na msm aba, Sair e voltar: o navegador devolvia a pagina inteira
    // guardada na memoria, com cpf e endereco, e a api ja dizendo 401
    it("a pagina q o navegador devolve da memoria recarrega e passa pela sessao de novo", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({message: "Sessão expirada"}), {status: 401}));
        await openAppAt("/perfil");
        // espera o router terminar, senao ele ainda monta dps q o teste acaba e baixa a tela do teste seguinte
        expect(await screen.findByLabelText("Senha", {}, {timeout: 5000})).toBeInTheDocument();
        const reload = vi.fn();
        vi.stubGlobal("location", {...window.location, reload});

        window.dispatchEvent(new PageTransitionEvent("pageshow", {persisted: false}));

        expect(reload).not.toHaveBeenCalled();

        window.dispatchEvent(new PageTransitionEvent("pageshow", {persisted: true}));

        expect(reload).toHaveBeenCalled();
    }, 15000);

    // o nginx tbm n deixa o navegador guardar o index, e as rotas do app chegam nele pelo try_files
    it("o index sai do nginx sem ficar guardado no navegador", () => {
        const nginx = readCss(import.meta.url, "../nginx.conf");
        const indexBlock = nginx.slice(nginx.indexOf("location = /index.html {"));

        expect(indexBlock.slice(0, indexBlock.indexOf("}"))).toContain("add_header Cache-Control \"no-store\" always;");
        expect(nginx).toContain("try_files $uri $uri/ /index.html;");
    });

    // no 3G cada ida e volta leva uns 2 s, e o codigo da tela so comecava a baixar dps da resposta de quem esta logado
    it("o codigo da tela do endereco comeca a baixar junto com a pergunta de quem esta logado", async () => {
        const pageDownloaded = vi.fn();
        vi.doMock("./pages/dashboard-paciente/dashboard-paciente.jsx", () => {
            pageDownloaded();
            return {default: () => null};
        });
        // a api ainda n respondeu
        vi.spyOn(globalThis, "fetch").mockImplementation(() => new Promise(() => {}));

        await openAppAt("/painel-paciente");

        await vi.waitFor(() => expect(pageDownloaded).toHaveBeenCalled());
        expect(globalThis.fetch).toHaveBeenCalledTimes(1);
        expect(document.getElementById("root")).toBeEmptyDOMElement();
        vi.doUnmock("./pages/dashboard-paciente/dashboard-paciente.jsx");
    });

    // o router baixa de novo e mostra o erro dele, entao a falha desse adiantamento n aparece em lugar nenhum
    it("sem internet o codigo adiantado falha calado e a tela de sem conexao abre igual", async () => {
        vi.doMock("./pages/progresso/progresso.jsx", () => {
            throw new TypeError("Failed to fetch dynamically imported module");
        });
        vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));

        await openAppAt("/progresso");

        expect(await screen.findByText(CONNECTION_ERROR_MESSAGE)).toBeInTheDocument();
        vi.doUnmock("./pages/progresso/progresso.jsx");
    });

    it("sem sessao a api responde 401 e o login abre como sempre", async () => {
        vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({message: "Sua sessão expirou ou foi encerrada. Entre de novo para continuar"}), {status: 401}));

        await openAppAt("/agendamento-consulta");

        expect(await screen.findByLabelText("Senha", {}, {timeout: 5000})).toBeInTheDocument();
        expect(window.location.pathname).toBe("/entrar");
        expect(screen.queryByText(CONNECTION_ERROR_MESSAGE)).not.toBeInTheDocument();
    }, 15000);
});
