import {fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError} from "../../services/api.js";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";
import AdminPrescribers from "./admin-prescribers.jsx";

const INVALID_CPF_MESSAGE = "Confira o CPF: os 11 números precisam ser iguais aos do documento";

const PRESCRIBERS = [
    {
        id: 1, name: "Ana Lima", email: "ana@email.com", profession: "Biomédica", registryType: "CRBM",
        registryNumber: "12345", active: true, passwordCreated: true,
    },
    {
        id: 2, name: "Bruno Novo", email: "brno@email.com", profession: "Médico", registryType: "CRM",
        registryNumber: "777", active: true, passwordCreated: false,
    },
];

describe("AdminPrescribers", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // desativar n fecha nada dos pacientes: a resposta diz o q ficou esperando e a tela mostra (#148)
    it("desativar diz quantas escalas e consultas dos pacientes ficaram esperando", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        const put = vi.spyOn(apiService, "put").mockResolvedValue({
            ...PRESCRIBERS[0], active: false, openTasks: 3, futureAppointments: 1,
        });
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        const buttons = await screen.findAllByRole("button", {name: "Desativar"});
        await user.click(buttons[0]);
        const dialog = screen.getByRole("dialog");
        expect(dialog).toHaveTextContent(/sem lembrete para os pacientes enquanto a conta estiver desativada/);
        // desativar so pausa o acompanhamento automatico (revisao das regras R18)
        expect(dialog).toHaveTextContent(/o acompanhamento automático dos pacientes dela fica pausado/);
        expect(dialog).not.toHaveTextContent(/encerrado/);
        await user.click(within(dialog).getByRole("button", {name: "Sim, desativar"}));

        expect(put).toHaveBeenCalledWith("/admin/prescribers/1/active", {active: false});
        const notice = await screen.findByText(/Acesso de Ana Lima desativado/);
        expect(notice).toHaveTextContent(/3 escalas esperando resposta/);
        expect(notice).toHaveTextContent(/1 consulta ou pedido de consulta por vir/);
        expect(notice).toHaveTextContent(/lembretes só voltam quando a conta for reativada/);
    });

    // o e-mail digitado errado so pode ser corrigido enquanto a pessoa n criou a senha (issue 131)
    it("corrige o e-mail de quem ainda nao criou a senha e mostra o link novo", async () => {
        // a lista q o GET devolve muda dps do PUT, igual na api de verdade
        let prescribers = PRESCRIBERS;
        vi.spyOn(apiService, "get").mockImplementation(() => Promise.resolve(prescribers));
        const put = vi.spyOn(apiService, "put").mockImplementation(() => {
            prescribers = [PRESCRIBERS[0], {...PRESCRIBERS[1], email: "bruno@email.com"}];
            return Promise.resolve({
                prescriberId: 2, prescriberName: "Bruno Novo", resetLink: "http://localhost:5173/senha-nova?token=abc",
                validMinutes: 2880,
            });
        });
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        const buttons = await screen.findAllByRole("button", {name: "Corrigir e-mail"});
        expect(buttons).toHaveLength(1);

        await user.click(buttons[0]);
        const emailInput = screen.getByLabelText("E-mail certo");
        expect(emailInput).toHaveValue("brno@email.com");
        await user.clear(emailInput);
        await user.type(emailInput, "Bruno@Email.com");
        await user.type(screen.getByLabelText("Sua senha de administrador"), "SenhaDoAdmin@2026");
        await user.click(screen.getByRole("button", {name: "Corrigir e reenviar o link"}));

        expect(put).toHaveBeenCalledWith("/admin/prescribers/2/email",
            {email: "Bruno@Email.com", adminPassword: "SenhaDoAdmin@2026"});
        expect(await screen.findByText(/O e-mail foi corrigido/)).toBeInTheDocument();
        expect(screen.getByLabelText("Link de senha")).toHaveValue("http://localhost:5173/senha-nova?token=abc");
        // a lista recarrega e mostra o e-mail do jeito q a api guardou
        expect(await screen.findByText("bruno@email.com")).toBeInTheDocument();
    });

    it("com e-mail configurado o link n aparece, so o aviso de q foi enviado", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        vi.spyOn(apiService, "put").mockResolvedValue({
            prescriberId: 2, prescriberName: "Bruno Novo", resetLink: null, validMinutes: 2880,
        });
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click(await screen.findByRole("button", {name: "Corrigir e-mail"}));
        const emailInput = screen.getByLabelText("E-mail certo");
        await user.clear(emailInput);
        await user.type(emailInput, "bruno@email.com");
        await user.type(screen.getByLabelText("Sua senha de administrador"), "SenhaDoAdmin@2026");
        await user.click(screen.getByRole("button", {name: "Corrigir e reenviar o link"}));

        expect(await screen.findByText(/enviado para bruno@email.com/)).toBeInTheDocument();
        expect(screen.getByText(/48 horas/)).toBeInTheDocument();
        expect(screen.queryByLabelText("Link de senha")).not.toBeInTheDocument();
    });

    it("o erro do campo aparece e o formulario continua aberto com o q foi digitado", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(409, "Confira os campos destacados",
            [{field: "email", message: "Este e-mail já tem conta no sistema"}]));
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click(await screen.findByRole("button", {name: "Corrigir e-mail"}));
        const emailInput = screen.getByLabelText("E-mail certo");
        await user.clear(emailInput);
        await user.type(emailInput, "ana@email.com");
        await user.type(screen.getByLabelText("Sua senha de administrador"), "SenhaDoAdmin@2026");
        await user.click(screen.getByRole("button", {name: "Corrigir e reenviar o link"}));

        expect(await screen.findByText("Este e-mail já tem conta no sistema")).toBeInTheDocument();
        expect(screen.getByLabelText("E-mail certo")).toHaveValue("ana@email.com");
    });

    // o erro do e-mail virava um aviso solto e o foco ficava no botao (CT-39)
    it("o e-mail recusado no Corrigir e-mail leva o foco pro campo e o leitor de tela le o erro junto", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(409, "Confira os campos destacados",
            [{field: "email", message: "Este e-mail já tem conta no sistema"}]));
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click(await screen.findByRole("button", {name: "Corrigir e-mail"}));
        const emailInput = screen.getByLabelText("E-mail certo");
        await user.clear(emailInput);
        await user.type(emailInput, "ana@email.com");
        await user.type(screen.getByLabelText("Sua senha de administrador"), "SenhaDoAdmin@2026");
        await user.click(screen.getByRole("button", {name: "Corrigir e reenviar o link"}));

        await waitFor(() => expect(emailInput).toHaveFocus());
        expect(emailInput).toHaveAccessibleDescription("Este e-mail já tem conta no sistema");
        expect(screen.getByLabelText("Sua senha de administrador")).not.toHaveAttribute("aria-invalid");
    });

    it("a senha de administrador recusada no Corrigir e-mail leva o foco pra ela", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(400, "Confira os campos destacados",
            [{field: "adminPassword", message: "A sua senha está incorreta"}]));
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click(await screen.findByRole("button", {name: "Corrigir e-mail"}));
        await user.type(screen.getByLabelText("Sua senha de administrador"), "senha-errada");
        await user.click(screen.getByRole("button", {name: "Corrigir e reenviar o link"}));

        const password = screen.getByLabelText("Sua senha de administrador");
        await waitFor(() => expect(password).toHaveFocus());
        expect(password).toHaveAccessibleDescription("A sua senha está incorreta");
        expect(screen.getByLabelText("E-mail certo")).not.toHaveAttribute("aria-invalid");
    });

    // a recusa q n eh de nenhum campo continua no aviso, q o leitor de tela anuncia sozinho
    it("a recusa sem campo no Corrigir e-mail aparece no aviso", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        vi.spyOn(apiService, "put").mockRejectedValue(
            new ApiError(400, "Essa pessoa já criou a senha. Ela mesma troca o e-mail pelo perfil"));
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click(await screen.findByRole("button", {name: "Corrigir e-mail"}));
        await user.type(screen.getByLabelText("Sua senha de administrador"), "SenhaDoAdmin@2026");
        await user.click(screen.getByRole("button", {name: "Corrigir e reenviar o link"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("Essa pessoa já criou a senha");
    });

    // o numero colado com espaco no fim virava outro registro na api
    it("manda o numero do registro sem espaco nas pontas", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        const post = vi.spyOn(apiService, "post").mockResolvedValue({
            prescriberId: 3, prescriberName: "Carla Nova", resetLink: null, validMinutes: 2880,
        });
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click(await screen.findByRole("button", {name: "Novo prescritor"}));
        await user.type(screen.getByLabelText("Nome completo"), "Carla Nova");
        await user.type(screen.getByLabelText("E-mail"), "carla@email.com");
        await user.type(screen.getByLabelText("CPF"), "168.995.350-09");
        await user.type(screen.getByLabelText("Profissão"), "Biomédica");
        await user.type(screen.getByLabelText("Número do registro"), " 4321 ");
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(post).toHaveBeenCalledWith("/admin/prescribers", expect.objectContaining({
            registryType: "CRBM",
            registryNumber: "4321",
        }));
    });

    // registro, cpf ou profissao digitados errado n tinham conserto (#149)
    it("corrige o registro e a profissao pelo Corrigir dados e a lista recarrega", async () => {
        let prescribers = PRESCRIBERS;
        vi.spyOn(apiService, "get").mockImplementation(() => Promise.resolve(prescribers));
        const put = vi.spyOn(apiService, "put").mockImplementation(() => {
            prescribers = [{...PRESCRIBERS[0], registryType: "CRM", registryNumber: "54321", profession: "Médica"},
                PRESCRIBERS[1]];
            return Promise.resolve(prescribers[0]);
        });
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        const buttons = await screen.findAllByRole("button", {name: "Corrigir dados"});
        expect(buttons).toHaveLength(2);
        await user.click(buttons[0]);

        // os campos ja vem com o q a lista mostra, e o cpf fica em branco pra so mandar se for corrigir
        expect(screen.getByLabelText("Profissão")).toHaveValue("Biomédica");
        expect(screen.getByLabelText("Conselho")).toHaveValue("CRBM");
        expect(screen.getByLabelText("Número do registro")).toHaveValue("12345");
        expect(screen.getByLabelText(/^CPF/)).toHaveValue("");

        await user.clear(screen.getByLabelText("Profissão"));
        await user.type(screen.getByLabelText("Profissão"), "Médica");
        await user.selectOptions(screen.getByLabelText("Conselho"), "CRM");
        await user.clear(screen.getByLabelText("Número do registro"));
        await user.type(screen.getByLabelText("Número do registro"), " 54321 ");
        await user.click(screen.getByRole("button", {name: "Salvar correção"}));

        expect(put).toHaveBeenCalledWith("/admin/prescribers/1",
            {cpf: null, profession: "Médica", registryType: "CRM", registryNumber: "54321"});
        expect(await screen.findByText("Dados de Ana Lima corrigidos.")).toBeInTheDocument();
        expect(screen.getByText("CRM 54321 · Médica")).toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Salvar correção"})).not.toBeInTheDocument();
    });

    it("o cpf vai so com numeros e o erro do campo da correcao aparece embaixo dele", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        const put = vi.spyOn(apiService, "put").mockRejectedValue(new ApiError(409, "Confira os campos destacados",
            [{field: "cpf", message: "Este CPF já tem conta no sistema"}]));
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click((await screen.findAllByRole("button", {name: "Corrigir dados"}))[1]);
        await user.type(screen.getByLabelText(/^CPF/), "111.444.777-35");
        await user.click(screen.getByRole("button", {name: "Salvar correção"}));

        expect(put).toHaveBeenCalledWith("/admin/prescribers/2",
            {cpf: "11144477735", profession: "Médico", registryType: "CRM", registryNumber: "777"});
        expect(await screen.findByText("Este CPF já tem conta no sistema")).toBeInTheDocument();
        expect(screen.getByLabelText(/^CPF/)).toHaveValue("111.444.777-35");
    });

    // so com letras o cpf ia vazio pra api e a tela dizia q era obrigatorio, com algo digitado no campo
    it("cpf invalido no cadastro avisa no campo q eh invalido e nem chega na api", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        const post = vi.spyOn(apiService, "post").mockResolvedValue({
            prescriberId: 3, prescriberName: "Carla Nova", resetLink: null, validMinutes: 2880,
        });
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click(await screen.findByRole("button", {name: "Novo prescritor"}));
        fireEvent.change(screen.getByLabelText("Nome completo"), {target: {value: "Carla Nova"}});
        fireEvent.change(screen.getByLabelText("E-mail"), {target: {value: "carla@email.com"}});
        fireEvent.change(screen.getByLabelText("Profissão"), {target: {value: "Biomédica"}});
        fireEvent.change(screen.getByLabelText("Número do registro"), {target: {value: "4321"}});
        await user.type(screen.getByLabelText("CPF"), "abc");
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText(INVALID_CPF_MESSAGE)).toBeInTheDocument();
        expect(screen.getByLabelText("CPF")).toHaveAttribute("aria-invalid", "true");
        expect(screen.queryByText("Informe o CPF")).not.toBeInTheDocument();
        expect(post).not.toHaveBeenCalled();

        // o digito verificador errado tbm fica na tela
        fireEvent.change(screen.getByLabelText("CPF"), {target: {value: "529.982.247-26"}});
        await user.click(screen.getByRole("button", {name: "Criar conta"}));
        expect(screen.getByText(INVALID_CPF_MESSAGE)).toBeInTheDocument();
        expect(post).not.toHaveBeenCalled();
    });

    // o erro aparecia embaixo do campo, mas o foco ficava no botao e o erro n era ligado ao campo (CT-39)
    it("o erro de campo do novo prescritor leva o foco pro campo e o leitor de tela le o erro junto", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(409, "Este e-mail já pertence a outra conta",
            [{field: "email", message: "Este e-mail já pertence a outra conta"}]));
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click(await screen.findByRole("button", {name: "Novo prescritor"}));
        fireEvent.change(screen.getByLabelText("Nome completo"), {target: {value: "Carla Nova"}});
        fireEvent.change(screen.getByLabelText("E-mail"), {target: {value: "ana@email.com"}});
        fireEvent.change(screen.getByLabelText("CPF"), {target: {value: "529.982.247-25"}});
        fireEvent.change(screen.getByLabelText("Profissão"), {target: {value: "Biomédica"}});
        fireEvent.change(screen.getByLabelText("Número do registro"), {target: {value: "4321"}});
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        const email = screen.getByLabelText("E-mail");
        await waitFor(() => expect(email).toHaveFocus());
        expect(email).toHaveAccessibleDescription("Este e-mail já pertence a outra conta");
    });

    // a recusa da senha do administrador so aparecia num aviso solto e o foco ficava no botao (CT-39)
    it("a senha de administrador recusada no Senha nova leva o foco pro campo e o leitor de tela le o erro junto", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(400, "Confira os campos destacados",
            [{field: "adminPassword", message: "A senha do administrador está incorreta"}]));
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click((await screen.findAllByRole("button", {name: "Senha nova"}))[0]);
        await user.type(screen.getByLabelText("Sua senha de administrador"), "senha-errada");
        await user.click(screen.getByRole("button", {name: "Gerar link"}));

        const password = screen.getByLabelText("Sua senha de administrador");
        await waitFor(() => expect(password).toHaveFocus());
        expect(password).toHaveAccessibleDescription("A senha do administrador está incorreta");
    });

    // eram os unicos campos de senha sem o olho, e a senha a mostra n voltava a esconder no envio
    it("a senha de administrador do Senha nova e do Corrigir e-mail tem o olho e se esconde de novo ao enviar", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(429, "Muitas tentativas. Espere um pouco"));
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click((await screen.findAllByRole("button", {name: "Senha nova"}))[0]);
        const password = screen.getByLabelText("Sua senha de administrador");
        expect(password).toHaveAttribute("type", "password");
        expect(password).toHaveAttribute("spellcheck", "false");
        expect(password).toBeRequired();
        await user.type(password, "senha-errada");
        await user.click(screen.getByRole("button", {name: "Mostrar senha"}));
        expect(password).toHaveAttribute("type", "text");
        await user.click(screen.getByRole("button", {name: "Gerar link"}));
        await waitFor(() => expect(password).toHaveAttribute("type", "password"));
        await user.click(screen.getByRole("button", {name: "Cancelar"}));

        await user.click(await screen.findByRole("button", {name: "Corrigir e-mail"}));
        const emailPassword = screen.getByLabelText("Sua senha de administrador");
        expect(emailPassword).toHaveAttribute("type", "password");
        expect(emailPassword).toBeRequired();
        await user.click(screen.getByRole("button", {name: "Mostrar senha"}));
        expect(emailPassword).toHaveAttribute("type", "text");
    });

    it("a recusa sem campo no Senha nova aparece no aviso", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        vi.spyOn(apiService, "post").mockRejectedValue(new ApiError(429, "Muitas tentativas. Espere um pouco"));
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click((await screen.findAllByRole("button", {name: "Senha nova"}))[0]);
        await user.type(screen.getByLabelText("Sua senha de administrador"), "senha-errada");
        await user.click(screen.getByRole("button", {name: "Gerar link"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("Muitas tentativas. Espere um pouco");
    });

    // na correcao o cpf so com letras virava null e a api mantinha o guardado sem avisar nada
    it("cpf invalido na correcao avisa no campo e n manda a correcao", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        const put = vi.spyOn(apiService, "put").mockResolvedValue(PRESCRIBERS[1]);
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        await user.click((await screen.findAllByRole("button", {name: "Corrigir dados"}))[1]);
        await user.type(screen.getByLabelText(/^CPF/), "abc");
        await user.click(screen.getByRole("button", {name: "Salvar correção"}));

        expect(await screen.findByText(INVALID_CPF_MESSAGE)).toBeInTheDocument();
        expect(screen.getByLabelText(/^CPF/)).toHaveAttribute("aria-invalid", "true");
        expect(put).not.toHaveBeenCalled();
        expect(screen.getByRole("button", {name: "Salvar correção"})).toBeInTheDocument();
    });

    // pra conta q nunca criou a senha o texto "a senha atual continua valendo" n fazia sentido (#154)
    it("o Senha nova de quem ainda nao criou a senha fala em criar a primeira senha", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue(PRESCRIBERS);
        const user = userEvent.setup();
        render(<AdminPrescribers/>);

        const buttons = await screen.findAllByRole("button", {name: "Senha nova"});
        await user.click(buttons[1]);
        expect(screen.getByText(/ainda não tem senha/)).toBeInTheDocument();
        expect(screen.queryByText(/A senha atual dela continua valendo/)).not.toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Cancelar"}));

        await user.click(buttons[0]);
        expect(screen.getByText(/A senha atual dela continua valendo/)).toBeInTheDocument();
        expect(screen.queryByText(/ainda não tem senha/)).not.toBeInTheDocument();
    });

    // no celular o space-between espalhava o selo e os botoes e deixava o Desativar sozinho num canto
    it("no celular os botoes da linha enchem a linha e o selo fica do tamanho dele", () => {
        const css = readCss(import.meta.url, "./admin-prescribers.module.css");
        const mobile = mediaRules(css, "@media (max-width: 47.99rem)");

        expect(declarationsOf(mobile, ".rowActions")["justify-content"]).toBeUndefined();
        expect(declarationsOf(mobile, ".rowActions").width).toBe("100%");
        expect(declarationsOf(mobile, ".rowActions > button").flex).toBe("1 1 auto");
        expect(declarationsOf(css, ".rowActions > button").flex).toBeUndefined();
    });
});
