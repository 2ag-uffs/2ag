import {fireEvent, render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {redirectLoggedUserHomeUnlessInvite} from "../../app/route-guards.js";
import {apiService, ApiError, getLoggedUser, setLoggedUser} from "../../services/api.js";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";
import SignUp from "./sign-up.jsx";

const INVITE = {prescriberName: "Ana Lima", prescriberProfession: "Médica", expiresAt: "2026-12-01T10:00:00"};
const OUTDATED_TERM_MESSAGE = "O termo de consentimento foi atualizado. Leia a versão nova e aceite para continuar";
const SIGN_UP_REFUSED_MESSAGE = "Não foi possível criar a conta com esses dados. Confira o e-mail e o CPF. "
    + "Se você já tem conta, entre pela tela de login, que também tem o Esqueci minha senha";
const INVALID_INVITE_MESSAGE = "Convite inválido ou vencido. Peça um novo link ao seu prescritor.";

function mockApi() {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        if (path === "/consent-term") {
            return Promise.resolve({version: "termo-1", text: "Texto do termo"});
        }
        return Promise.resolve({version: "pesquisa-1", text: "Texto da pesquisa"});
    });
    return vi.spyOn(apiService, "post").mockImplementation((path) => {
        if (path === "/invites/lookup") {
            return Promise.resolve(INVITE);
        }
        return Promise.resolve({id: 9, name: "Ana Souza", role: "PATIENT", termPending: false});
    });
}

// o convite vale e o cadastro volta com o erro dado
function mockRefusedSignUp(registerError) {
    const post = mockApi();
    post.mockImplementation((path) => path === "/invites/lookup"
        ? Promise.resolve(INVITE)
        : Promise.reject(registerError));
    return post;
}

function renderSignUp(path = "/cadastro?convite=abc") {
    const router = createMemoryRouter([
        {path: "/cadastro", loader: redirectLoggedUserHomeUnlessInvite, element: <SignUp/>},
        {path: "/painel-paciente", element: <p>painel do paciente</p>},
    ], {initialEntries: [path]});
    render(<RouterProvider router={router}/>);
    return router;
}

// 1o de janeiro nunca vira data invalida (29/02) e a idade fica certa o ano todo
// alguem com 10 anos hoje
function childBirthDate() {
    return (new Date().getFullYear() - 10) + "-01-01";
}

// alguem com 17 anos hoje
function teenBirthDate() {
    return (new Date().getFullYear() - 17) + "-01-01";
}

// preenche so o q o cadastro de um adulto exige
async function fillRequiredFields(user, birthDate = "1990-04-12") {
    await user.type(await screen.findByLabelText("Nome completo"), "Maria da Silva");
    await user.type(screen.getByLabelText("CPF"), "52998224725");
    fireEvent.change(screen.getByLabelText("Data de nascimento"), {target: {value: birthDate}});
    await user.type(screen.getAllByLabelText("Telefone com DDD")[0], "49999887766");
    await user.type(screen.getByLabelText("Rua"), "Rua das Flores");
    await user.type(screen.getByLabelText("Número"), "120");
    await user.type(screen.getByLabelText("Cidade"), "Chapecó");
    await user.selectOptions(screen.getByLabelText("Estado"), "SC");
    await user.type(screen.getByLabelText("E-mail"), "maria@email.com");
    await user.type(screen.getByLabelText("Senha"), "Minha#Senha1");
    await user.type(screen.getByLabelText("Repita a senha"), "Minha#Senha1");
    await user.click(screen.getByRole("checkbox", {name: /termo de consentimento/}));
}

// digitar o formulario inteiro leva uns 2s, e com a maquina ocupada passa dos 5s padrao
const TYPING_TIMEOUT = 15000;

describe("SignUp", () => {
    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
    });

    it("manda o nome social junto do nome completo", async () => {
        const post = mockApi();
        const user = userEvent.setup();
        renderSignUp();

        await fillRequiredFields(user);
        await user.type(screen.getByLabelText("Nome social (opcional)"), " Ana Souza ");
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/auth/register", expect.objectContaining({
            name: "Maria da Silva",
            socialName: "Ana Souza",
        }));
    }, TYPING_TIMEOUT);

    it("sem nome social o campo vai como nulo", async () => {
        const post = mockApi();
        const user = userEvent.setup();
        renderSignUp();

        await fillRequiredFields(user);
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/auth/register", expect.objectContaining({socialName: null}));
    }, TYPING_TIMEOUT);

    // menor de 18: qm preenche e aceita eh o responsavel legal, e a pesquisa ainda n abre pra menor
    it("data de nascimento de crianca pede o responsavel legal e esconde a pesquisa", async () => {
        mockApi();
        renderSignUp();

        const birthDateField = await screen.findByLabelText("Data de nascimento");
        expect(screen.getByRole("group", {name: "Familiar ou cuidador (opcional)"})).toBeInTheDocument();
        expect(screen.getByRole("checkbox", {name: /pesquisa/})).toBeInTheDocument();

        fireEvent.change(birthDateField, {target: {value: childBirthDate()}});

        expect(screen.getByRole("group", {name: "Responsável legal"})).toBeInTheDocument();
        expect(screen.getByLabelText("Nome")).toBeRequired();
        expect(screen.getByLabelText("Parentesco")).toBeRequired();
        expect(screen.getByRole("checkbox", {name: /Sou o responsável legal pelo paciente e aceito, em nome dele/}))
            .toBeInTheDocument();
        expect(screen.queryByRole("checkbox", {name: /pesquisa/})).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "termo de consentimento"}));
        expect(screen.getByRole("button", {name: "Li e aceito como responsável"})).toBeInTheDocument();
    });

    // com 16 e 17 o paciente aceita junto com o responsavel (Codigo Civil art. 4)
    it("paciente de 16 ou 17 anos aceita o termo junto com o responsavel legal", async () => {
        mockApi();
        renderSignUp();

        fireEvent.change(await screen.findByLabelText("Data de nascimento"), {target: {value: teenBirthDate()}});

        expect(screen.getByRole("group", {name: "Responsável legal"})).toBeInTheDocument();
        expect(screen.getByText(/O paciente tem 16 ou 17 anos/)).toBeInTheDocument();
        expect(screen.getByLabelText("Nome")).toBeRequired();
        expect(screen.getByRole("checkbox", {name: /Eu, paciente, e meu responsável legal lemos e aceitamos/}))
            .toBeInTheDocument();
        expect(screen.queryByRole("checkbox", {name: /Sou o responsável legal/})).not.toBeInTheDocument();
        expect(screen.queryByRole("checkbox", {name: /pesquisa/})).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", {name: "termo de consentimento"}));
        fireEvent.click(screen.getByRole("button", {name: "Lemos e aceitamos"}));
        expect(screen.getByRole("checkbox", {name: /lemos e aceitamos/})).toBeChecked();
    });

    // trocar de faixa troca qm marca o aceite, entao a marcacao antiga n vale
    it("trocar a data pra outra faixa de idade desmarca o termo e a pesquisa", async () => {
        mockApi();
        renderSignUp();

        const birthDateField = await screen.findByLabelText("Data de nascimento");
        fireEvent.change(birthDateField, {target: {value: "1990-04-12"}});
        fireEvent.click(screen.getByRole("checkbox", {name: /Li e aceito o/}));
        fireEvent.click(screen.getByRole("checkbox", {name: /pesquisa/}));

        // msm faixa: continua marcado
        fireEvent.change(birthDateField, {target: {value: "1985-07-01"}});
        expect(screen.getByRole("checkbox", {name: /Li e aceito o/})).toBeChecked();
        expect(screen.getByRole("checkbox", {name: /pesquisa/})).toBeChecked();

        fireEvent.change(birthDateField, {target: {value: teenBirthDate()}});
        const teenConsent = screen.getByRole("checkbox", {name: /lemos e aceitamos/});
        expect(teenConsent).not.toBeChecked();
        fireEvent.click(teenConsent);

        fireEvent.change(birthDateField, {target: {value: childBirthDate()}});
        expect(screen.getByRole("checkbox", {name: /Sou o responsável legal/})).not.toBeChecked();

        // voltando pra adulto a pesquisa tbm aparece desmarcada
        fireEvent.change(birthDateField, {target: {value: "1990-04-12"}});
        expect(screen.getByRole("checkbox", {name: /Li e aceito o/})).not.toBeChecked();
        expect(screen.getByRole("checkbox", {name: /pesquisa/})).not.toBeChecked();
    });

    // o e-mail eh unico (RN04) e a recusa do cadastro n diz qual campo repetiu, entao o responsavel com dois
    // filhos fica sabendo antes q cada conta precisa do seu e-mail (issue 152)
    it("cadastro de menor avisa embaixo do e-mail q cada paciente precisa de um e-mail proprio", async () => {
        mockApi();
        renderSignUp();

        const birthDateField = await screen.findByLabelText("Data de nascimento");
        expect(screen.queryByText(/e-mail próprio/)).not.toBeInTheDocument();

        fireEvent.change(birthDateField, {target: {value: childBirthDate()}});
        expect(screen.getByLabelText("E-mail").parentElement)
            .toHaveTextContent(/Cada paciente precisa de um e-mail próprio, mesmo que o responsável seja o mesmo/);

        fireEvent.change(birthDateField, {target: {value: teenBirthDate()}});
        expect(screen.getByText(/Cada paciente precisa de um e-mail próprio/)).toBeInTheDocument();

        // adulto n ve a frase
        fireEvent.change(birthDateField, {target: {value: "1990-04-12"}});
        expect(screen.queryByText(/e-mail próprio/)).not.toBeInTheDocument();
    });

    it("responsavel legal em branco n deixa criar a conta do menor", async () => {
        const post = mockApi();
        const user = userEvent.setup();
        renderSignUp();

        await fillRequiredFields(user, childBirthDate());
        await user.type(screen.getByLabelText("Nome"), " ");
        await user.type(screen.getByLabelText("Parentesco"), " ");
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText("Informe o nome do responsável legal")).toBeInTheDocument();
        expect(screen.getByText("Informe o parentesco do responsável legal")).toBeInTheDocument();
        expect(post).not.toHaveBeenCalledWith("/auth/register", expect.anything());
    }, TYPING_TIMEOUT);

    // o digito verificador eh conferido no navegador antes de mandar, com a mesma mensagem da api
    it("cpf com digito verificador errado avisa no campo e n manda", async () => {
        const post = mockApi();
        const user = userEvent.setup();
        renderSignUp();

        await fillRequiredFields(user);
        await user.clear(screen.getByLabelText("CPF"));
        await user.type(screen.getByLabelText("CPF"), "52998224726");
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText("CPF inválido. Confira os números")).toBeInTheDocument();
        expect(post).not.toHaveBeenCalledWith("/auth/register", expect.anything());
    }, TYPING_TIMEOUT);

    // o leitor de tela dizia so q a caixa era invalida, sem o motivo, pq a msg n era ligada a ela
    it("sem aceitar o termo o erro fica ligado a caixa do termo", async () => {
        const post = mockApi();
        const user = userEvent.setup();
        renderSignUp();

        await fillRequiredFields(user);
        const consent = screen.getByRole("checkbox", {name: /termo de consentimento/});
        await user.click(consent);
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText("Para criar a conta, leia e aceite o termo de consentimento"))
            .toBeInTheDocument();
        expect(consent).toHaveAttribute("aria-invalid", "true");
        expect(consent).toHaveAccessibleDescription("Para criar a conta, leia e aceite o termo de consentimento");
        expect(post).not.toHaveBeenCalledWith("/auth/register", expect.anything());

        // marcada a caixa o erro some e a descricao tbm
        await user.click(consent);
        expect(consent).not.toHaveAttribute("aria-describedby");
    }, TYPING_TIMEOUT);

    it("menor manda o responsavel legal e nunca a pesquisa, mesmo marcada antes", async () => {
        const post = mockApi();
        const user = userEvent.setup();
        renderSignUp();

        await user.click(await screen.findByRole("checkbox", {name: /pesquisa/}));
        await fillRequiredFields(user, childBirthDate());
        await user.type(screen.getByLabelText("Nome"), "Joana Souza");
        await user.type(screen.getByLabelText("Parentesco"), "mãe");
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(post).toHaveBeenCalledWith("/auth/register", expect.objectContaining({
            guardian: {name: "Joana Souza", relationship: "mãe", phone: null},
            researchTermVersion: null,
        }));
    }, TYPING_TIMEOUT);

    // o deploy de um termo novo pode chegar com a pessoa no meio do cadastro
    it("qnd o termo muda com a tela aberta, ler e aceitar de novo manda a versao nova", async () => {
        let termVersion = "termo-1";
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(path === "/consent-term"
            ? {version: termVersion, text: "Texto do termo " + termVersion}
            : {version: "pesquisa-1", text: "Texto da pesquisa"}));
        const post = vi.spyOn(apiService, "post").mockImplementation((path, body) => {
            if (path === "/invites/lookup") {
                return Promise.resolve(INVITE);
            }
            if (body.consentTermVersion !== termVersion) {
                return Promise.reject(new ApiError(400, OUTDATED_TERM_MESSAGE));
            }
            return Promise.resolve({id: 9, name: "Maria da Silva", role: "PATIENT", termPending: false});
        });
        const user = userEvent.setup();
        renderSignUp();

        await fillRequiredFields(user);
        termVersion = "termo-2";
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText(OUTDATED_TERM_MESSAGE)).toBeInTheDocument();
        expect(screen.getByRole("checkbox", {name: /termo de consentimento/})).not.toBeChecked();
        await user.click(screen.getByRole("button", {name: "termo de consentimento"}));
        expect(screen.getByText("Texto do termo termo-2")).toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Li e aceito"}));
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(post).toHaveBeenLastCalledWith("/auth/register", expect.objectContaining({consentTermVersion: "termo-2"}));
    }, TYPING_TIMEOUT);

    // a clinica troca o contato do encarregado e reinicia a api com a tela aberta: o texto muda e a versao n
    it("qnd so o texto do termo muda com a tela aberta, o texto novo aparece e o aceite desmarca", async () => {
        let termText = "Texto do termo com contato-a";
        vi.spyOn(apiService, "get").mockImplementation((path) => Promise.resolve(path === "/consent-term"
            ? {version: "termo-1", text: termText, textHash: "hash de " + termText}
            : {version: "pesquisa-1", text: "Texto da pesquisa", textHash: "hash da pesquisa"}));
        const post = vi.spyOn(apiService, "post").mockImplementation((path) => path === "/invites/lookup"
            ? Promise.resolve(INVITE)
            : Promise.reject(new ApiError(400, OUTDATED_TERM_MESSAGE)));
        const user = userEvent.setup();
        renderSignUp();

        await fillRequiredFields(user);
        termText = "Texto do termo com contato-b";
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText(OUTDATED_TERM_MESSAGE)).toBeInTheDocument();
        // a api confere o hash do texto q a tela mostrou
        expect(post).toHaveBeenCalledWith("/auth/register", expect.objectContaining({
            consentTermVersion: "termo-1", consentTermHash: "hash de Texto do termo com contato-a",
        }));
        expect(screen.getByRole("checkbox", {name: /termo de consentimento/})).not.toBeChecked();
        await user.click(screen.getByRole("button", {name: "termo de consentimento"}));
        expect(screen.getByText("Texto do termo com contato-b")).toBeInTheDocument();
    }, TYPING_TIMEOUT);

    // a msg manda entrar ou usar o esqueci minha senha, entao o caminho fica logo embaixo dela (pedido da qa)
    // ela continua sem dizer se foi o e-mail ou o cpf, e o q foi digitado fica pq a pessoa pode so ter errado
    it("e-mail ou cpf q ja tem conta mostra os links de entrar e de senha nova sem apagar o formulario", async () => {
        mockRefusedSignUp(new ApiError(400, SIGN_UP_REFUSED_MESSAGE));
        const user = userEvent.setup();
        renderSignUp();

        await fillRequiredFields(user);
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByRole("alert")).toHaveTextContent(SIGN_UP_REFUSED_MESSAGE);
        const accountLinks = screen.getByRole("group", {name: "Se você já tem conta"});
        expect(within(accountLinks).getByRole("link", {name: "Entrar"})).toHaveAttribute("href", "/entrar");
        expect(within(accountLinks).getByRole("link", {name: "Esqueci minha senha"}))
            .toHaveAttribute("href", "/esqueci-senha");
        expect(screen.getByLabelText("Nome completo")).toHaveValue("Maria da Silva");
        expect(screen.getByLabelText("CPF")).toHaveValue("529.982.247-25");
        expect(screen.getByLabelText("E-mail")).toHaveValue("maria@email.com");
        expect(screen.getByRole("checkbox", {name: /termo de consentimento/})).toBeChecked();
    }, TYPING_TIMEOUT);

    it("erro de campo e convite ja usado n mostram os links de entrar e de senha nova", async () => {
        const post = mockRefusedSignUp(new ApiError(400, "Confira os campos destacados",
            [{field: "email", message: "Informe um e-mail válido"}]));
        const user = userEvent.setup();
        renderSignUp();

        await fillRequiredFields(user);
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByText("Informe um e-mail válido")).toBeInTheDocument();
        expect(screen.queryByRole("group", {name: "Se você já tem conta"})).not.toBeInTheDocument();
        expect(screen.queryByRole("link", {name: "Esqueci minha senha"})).not.toBeInTheDocument();

        post.mockImplementation((path) => path === "/invites/lookup"
            ? Promise.resolve(INVITE)
            : Promise.reject(new ApiError(400, INVALID_INVITE_MESSAGE)));
        await user.click(screen.getByRole("button", {name: "Criar conta"}));

        expect(await screen.findByRole("alert")).toHaveTextContent(INVALID_INVITE_MESSAGE);
        expect(screen.queryByRole("group", {name: "Se você já tem conta"})).not.toBeInTheDocument();
        expect(screen.queryByRole("link", {name: "Esqueci minha senha"})).not.toBeInTheDocument();
    }, TYPING_TIMEOUT);

    // responsavel com dois filhos logado na conta do primeiro abre o convite do segundo (RF38)
    it("convite aberto com outra conta logada explica e deixa sair pra seguir o cadastro", async () => {
        const post = mockApi();
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue({status: 204, ok: true});
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        const user = userEvent.setup();
        renderSignUp();

        expect(await screen.findByText(/Você está conectado como/)).toHaveTextContent("Lucas Souza");
        // o convite eh conferido antes de pedir pra sair da conta
        expect(post).toHaveBeenCalledWith("/invites/lookup", {token: "abc"});
        expect(fetchSpy).not.toHaveBeenCalled();
        await user.click(screen.getByRole("button", {name: "Sair e continuar o cadastro"}));

        expect(await screen.findByLabelText("Nome completo")).toBeInTheDocument();
        expect(fetchSpy).toHaveBeenCalledWith("/api/auth/logout", expect.objectContaining({method: "POST"}));
    });

    // sem a api confirmar a saida o cookie continua valendo, entao a tela avisa e o convite fica na url
    it("se sair falhar a tela avisa, o convite fica no link e a pessoa continua logada", async () => {
        mockApi();
        vi.spyOn(globalThis, "fetch").mockResolvedValue({ok: false, status: 502});
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        const user = userEvent.setup();
        const router = renderSignUp();

        await user.click(await screen.findByRole("button", {name: "Sair e continuar o cadastro"}));

        expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível sair da conta");
        expect(screen.getByText(/Você está conectado como/)).toHaveTextContent("Lucas Souza");
        expect(screen.getByRole("button", {name: "Sair e continuar o cadastro"})).toBeInTheDocument();
        expect(screen.queryByLabelText("Nome completo")).not.toBeInTheDocument();
        expect(router.state.location.search).toBe("?convite=abc");
        expect(getLoggedUser()).toEqual(expect.objectContaining({name: "Lucas Souza"}));
    });

    // paciente q ja se cadastrou abre de novo o link do whatsapp pra voltar pro app
    it("convite ja usado com uma conta aberta n pede pra sair da conta", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({version: "termo-1", text: "Texto do termo"});
        vi.spyOn(apiService, "post").mockImplementation((path) => path === "/invites/lookup"
            ? Promise.reject(new ApiError(404, "Convite inválido ou vencido. Peça um novo link ao seu prescritor."))
            : Promise.resolve(null));
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue({status: 204, ok: true});
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT", termPending: false});
        const user = userEvent.setup();
        renderSignUp();

        const message = await screen.findByText(/Este convite não vale mais/);
        expect(message).toHaveTextContent("Você continua conectado como Maria Souza");
        expect(screen.queryByText(/saia dessa conta/)).not.toBeInTheDocument();
        expect(screen.queryByRole("button", {name: "Sair e continuar o cadastro"})).not.toBeInTheDocument();

        await user.click(screen.getByRole("link", {name: "Voltar para a conta aberta"}));
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
        expect(fetchSpy).not.toHaveBeenCalled();
        expect(getLoggedUser()).toEqual(expect.objectContaining({name: "Maria Souza"}));
    });

    it("sem convite qm esta logado volta pra tela inicial", async () => {
        mockApi();
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        renderSignUp("/cadastro");

        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
    });

    // em 768 o estado tinha 73px e Santa Catarina virava "Santa Ca", e ate 1440 Rio Grande do Norte cortava (CT-38).
    // cidade e estado so ficam lado a lado qnd cada um tem 11rem, onde cabe o nome mais comprido com a seta
    it("cidade e estado so ficam lado a lado qnd o nome mais comprido do estado cabe", async () => {
        mockApi();
        renderSignUp();

        const cityRow = (await screen.findByLabelText("Estado")).closest("div").parentElement;
        expect(cityRow.className).toMatch(/cityRow/);
        expect(within(cityRow).getByLabelText("Cidade")).toBeInTheDocument();
        const css = readCss(import.meta.url, "./sign-up.module.css");
        expect(declarationsOf(css, ".cityRow")["grid-template-columns"]).toBe("repeat(auto-fit, minmax(11rem, 1fr))");
        const mobile = mediaRules(css, "@media (max-width: 47.99rem)");
        expect(declarationsOf(mobile, ".cityRow")["grid-template-columns"]).toBe("minmax(0, 1fr)");
    });

    // o mesmo desenho do perfil: cidade e estado lado a lado cortavam o nome do estado no celular (CT-38)
    it("no celular rua, numero, cidade e estado ficam um embaixo do outro", () => {
        const mobile = mediaRules(readCss(import.meta.url, "./sign-up.module.css"), "@media (max-width: 47.99rem)");

        expect(declarationsOf(mobile, ".rowWide")["grid-template-columns"]).toBe("minmax(0, 1fr)");
    });
});
