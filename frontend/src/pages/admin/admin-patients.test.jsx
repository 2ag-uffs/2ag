import {render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError} from "../../services/api.js";
import AdminPatients from "./admin-patients.jsx";

describe("AdminPatients", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // a administracao confere a conta, entao ve o nome civil junto do social (RN15)
    it("mostra o nome civil qnd a busca devolve o civilName", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([
            {id: 2, name: "Bruno Lima", email: "bruno@email.com", active: false},
        ]);
        const post = vi.spyOn(apiService, "post").mockResolvedValue({
            id: 1, name: "Ana Souza", email: "ana@email.com", active: true, civilName: "Ana Civil",
        });
        const user = userEvent.setup();
        render(<AdminPatients/>);

        expect(await screen.findByText("bruno@email.com")).toBeInTheDocument();
        await user.type(screen.getByLabelText("E-mail da conta do paciente"), "ana@email.com");
        await user.click(screen.getByRole("button", {name: "Buscar"}));

        expect(post).toHaveBeenCalledWith("/admin/patients/lookup", {email: "ana@email.com"});
        expect(await screen.findByText("Ana Souza")).toBeInTheDocument();
        expect(screen.getByText("Nome civil: Ana Civil · ana@email.com")).toBeInTheDocument();
        // a conta sem nome social n mostra o nome civil
        expect(screen.getAllByText(/Nome civil/)).toHaveLength(1);
    });

    // a busca e a lista devolvem de quem eh cada numero, entao vao pra auditoria, e a tela diz isso
    // (revisao das regras R20)
    it("avisa q a busca e a lista das desativadas ficam na auditoria", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([]);
        render(<AdminPatients/>);

        expect(await screen.findByText(/A busca pelo e-mail e a lista das contas desativadas ficam registradas na auditoria/))
            .toBeInTheDocument();
    });

    // com internet lenta a linha n mudava e o segundo clique voltava com "ja esta desativada" em vermelho
    // pra uma acao q tinha dado certo (decisao da dona, D5)
    it("a linha fica ocupada enquanto a api desativa a conta e o botao n aceita outro clique", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([]);
        vi.spyOn(apiService, "post").mockResolvedValue({id: 1, name: "Ana Souza", email: "ana@email.com", active: true});
        let finishPut = null;
        const put = vi.spyOn(apiService, "put").mockImplementation(() => new Promise((resolve) => {
            finishPut = resolve;
        }));
        const user = userEvent.setup();
        render(<AdminPatients/>);

        await user.type(screen.getByLabelText("E-mail da conta do paciente"), "ana@email.com");
        await user.click(screen.getByRole("button", {name: "Buscar"}));
        await user.click(await screen.findByRole("button", {name: "Desativar"}));
        await user.click(within(screen.getByRole("dialog")).getByRole("button", {name: "Sim, desativar"}));

        const busyButton = await screen.findByRole("button", {name: "Desativando..."});
        expect(busyButton).toHaveAttribute("aria-disabled", "true");
        expect(busyButton.closest("li")).toHaveAttribute("aria-busy", "true");
        await user.click(busyButton);
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        expect(put).toHaveBeenCalledTimes(1);

        finishPut({id: 1, name: "Ana Souza", email: "ana@email.com", active: false});
        const reactivateButton = await screen.findByRole("button", {name: "Reativar"});
        expect(reactivateButton).not.toHaveAttribute("aria-disabled");
        expect(reactivateButton.closest("li")).not.toHaveAttribute("aria-busy");
        expect(screen.getByText("Acesso de Ana Souza desativado.")).toBeInTheDocument();
    });

    // outra janela desativou antes: a recusa aparece, mas a linha e a lista passam a mostrar a conta
    // como ela esta, e a busca seguinte limpa o aviso vermelho (decisao da dona, D5)
    it("a recusa de conta q ja esta desativada atualiza a linha e a lista, e a busca nova limpa o aviso", async () => {
        const get = vi.spyOn(apiService, "get").mockResolvedValue([]);
        vi.spyOn(apiService, "post").mockResolvedValue({id: 1, name: "Ana Souza", email: "ana@email.com", active: true});
        vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(400, "Esta conta já está desativada"));
        const user = userEvent.setup();
        render(<AdminPatients/>);

        await user.type(screen.getByLabelText("E-mail da conta do paciente"), "ana@email.com");
        await user.click(screen.getByRole("button", {name: "Buscar"}));
        await user.click(await screen.findByRole("button", {name: "Desativar"}));
        await user.click(within(screen.getByRole("dialog")).getByRole("button", {name: "Sim, desativar"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("Esta conta já está desativada");
        expect(screen.getByRole("button", {name: "Reativar"})).toBeInTheDocument();
        expect(screen.getByText("Desativada")).toBeInTheDocument();
        expect(get).toHaveBeenCalledTimes(2);

        await user.click(screen.getByRole("button", {name: "Buscar"}));
        expect(await screen.findByRole("button", {name: "Desativar"})).toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    // a busca fica livre enquanto uma linha esta ocupada. a resposta da primeira conta n pode
    // trocar a conta da busca nova por ela
    it("a resposta de uma conta ocupada n troca a conta q a busca nova achou", async () => {
        const get = vi.spyOn(apiService, "get").mockResolvedValue([]);
        vi.spyOn(apiService, "post").mockImplementation((path, body) => Promise.resolve(body.email === "ana@email.com"
            ? {id: 1, name: "Ana Souza", email: "ana@email.com", active: true}
            : {id: 2, name: "Bruno Lima", email: "bruno@email.com", active: true}));
        let finishPut = null;
        vi.spyOn(apiService, "put").mockImplementation(() => new Promise((resolve) => {
            finishPut = resolve;
        }));
        const user = userEvent.setup();
        render(<AdminPatients/>);

        const emailField = screen.getByLabelText("E-mail da conta do paciente");
        await user.type(emailField, "ana@email.com");
        await user.click(screen.getByRole("button", {name: "Buscar"}));
        await user.click(await screen.findByRole("button", {name: "Desativar"}));
        await user.click(within(screen.getByRole("dialog")).getByRole("button", {name: "Sim, desativar"}));
        await screen.findByRole("button", {name: "Desativando..."});

        await user.clear(emailField);
        await user.type(emailField, "bruno@email.com");
        await user.click(screen.getByRole("button", {name: "Buscar"}));
        expect(await screen.findByText("Bruno Lima")).toBeInTheDocument();

        get.mockResolvedValue([{id: 1, name: "Ana Souza", email: "ana@email.com", active: false}]);
        finishPut({id: 1, name: "Ana Souza", email: "ana@email.com", active: false});

        expect(await screen.findByText("Acesso de Ana Souza desativado.")).toBeInTheDocument();
        // a ana aparece so na lista das desativadas, e o bruno continua sendo a conta da busca
        expect(await screen.findByRole("button", {name: "Reativar"})).toBeInTheDocument();
        expect(screen.getByText("Bruno Lima")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Desativar"})).not.toHaveAttribute("aria-disabled");
        expect(screen.getAllByRole("listitem")).toHaveLength(2);
    });

    it("a recusa de uma conta ocupada n troca a conta q a busca nova achou", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([]);
        vi.spyOn(apiService, "post").mockImplementation((path, body) => Promise.resolve(body.email === "ana@email.com"
            ? {id: 1, name: "Ana Souza", email: "ana@email.com", active: true}
            : {id: 2, name: "Bruno Lima", email: "bruno@email.com", active: true}));
        let refusePut = null;
        vi.spyOn(apiService, "put").mockImplementation(() => new Promise((resolve, reject) => {
            refusePut = reject;
        }));
        const user = userEvent.setup();
        render(<AdminPatients/>);

        const emailField = screen.getByLabelText("E-mail da conta do paciente");
        await user.type(emailField, "ana@email.com");
        await user.click(screen.getByRole("button", {name: "Buscar"}));
        await user.click(await screen.findByRole("button", {name: "Desativar"}));
        await user.click(within(screen.getByRole("dialog")).getByRole("button", {name: "Sim, desativar"}));
        await screen.findByRole("button", {name: "Desativando..."});

        await user.clear(emailField);
        await user.type(emailField, "bruno@email.com");
        await user.click(screen.getByRole("button", {name: "Buscar"}));
        expect(await screen.findByText("Bruno Lima")).toBeInTheDocument();

        refusePut(new ApiError(400, "Esta conta já está desativada"));

        expect(await screen.findByRole("alert")).toHaveTextContent("Esta conta já está desativada");
        expect(screen.getByText("Bruno Lima")).toBeInTheDocument();
        expect(screen.queryByText("Ana Souza")).not.toBeInTheDocument();
        expect(screen.getByText("Ativa")).toBeInTheDocument();
    });

    // sem resposta da api nada mudou, entao a linha fica como estava e so o aviso aparece
    it("sem conexao a linha volta a aceitar o clique e continua como estava", async () => {
        const get = vi.spyOn(apiService, "get").mockResolvedValue([]);
        vi.spyOn(apiService, "post").mockResolvedValue({id: 1, name: "Ana Souza", email: "ana@email.com", active: true});
        vi.spyOn(apiService, "put").mockRejectedValue(new TypeError("Failed to fetch"));
        const user = userEvent.setup();
        render(<AdminPatients/>);

        await user.type(screen.getByLabelText("E-mail da conta do paciente"), "ana@email.com");
        await user.click(screen.getByRole("button", {name: "Buscar"}));
        await user.click(await screen.findByRole("button", {name: "Desativar"}));
        await user.click(within(screen.getByRole("dialog")).getByRole("button", {name: "Sim, desativar"}));

        expect(await screen.findByRole("alert")).toHaveTextContent(/Não foi possível alterar a conta/);
        expect(screen.getByRole("button", {name: "Desativar"})).not.toHaveAttribute("aria-disabled");
        expect(get).toHaveBeenCalledTimes(1);
    });

    // n achar alguem n eh erro de quem digitou: vira o aviso neutro com a lupa, igual a lista vazia
    // do prescritor, e o campo n fica vermelho (decisao da dona, V-P9)
    it("busca sem resultado mostra o aviso neutro com a lupa e o campo n fica vermelho", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([]);
        const notFoundMessage = "Nenhuma conta de paciente com este e-mail. Confira se ele foi digitado certo";
        const post = vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(404, notFoundMessage));
        const user = userEvent.setup();
        render(<AdminPatients/>);

        const emailField = screen.getByLabelText("E-mail da conta do paciente");
        await user.type(emailField, "ninguem@email.com");
        await user.click(screen.getByRole("button", {name: "Buscar"}));

        const notice = await screen.findByRole("status");
        expect(notice).toHaveTextContent(notFoundMessage);
        expect(notice.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(emailField).not.toHaveAttribute("aria-invalid");
        expect(post).toHaveBeenCalledWith("/admin/patients/lookup", {email: "ninguem@email.com"});

        // a busca seguinte q acha a conta tira o aviso
        post.mockResolvedValue({id: 1, name: "Ana Souza", email: "ana@email.com", active: true});
        await user.click(screen.getByRole("button", {name: "Buscar"}));
        expect(await screen.findByText("Ana Souza")).toBeInTheDocument();
        expect(screen.queryByText(notFoundMessage)).not.toBeInTheDocument();
    });

    it("e-mail mal digitado deixa o campo vermelho e mostra a mensagem como erro", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([]);
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(400, "Confira os campos", [
            {field: "email", message: "Confira o e-mail: ele precisa ter @ e o endereço completo"},
        ]));
        const user = userEvent.setup();
        render(<AdminPatients/>);

        const emailField = screen.getByLabelText("E-mail da conta do paciente");
        await user.type(emailField, "ana@email");
        await user.click(screen.getByRole("button", {name: "Buscar"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("Confira o e-mail");
        expect(emailField).toHaveAttribute("aria-invalid", "true");
        expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });

    // erro q n eh do e-mail (sem conexao) continua no aviso de erro, mas n pinta o campo
    it("sem conexao a busca mostra o erro e o campo n fica vermelho", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([]);
        vi.spyOn(apiService, "post").mockRejectedValue(new TypeError("Failed to fetch"));
        const user = userEvent.setup();
        render(<AdminPatients/>);

        const emailField = screen.getByLabelText("E-mail da conta do paciente");
        await user.type(emailField, "ana@email.com");
        await user.click(screen.getByRole("button", {name: "Buscar"}));

        expect(await screen.findByRole("alert")).toHaveTextContent(/Não foi possível conectar/);
        expect(emailField).not.toHaveAttribute("aria-invalid");
    });
});
