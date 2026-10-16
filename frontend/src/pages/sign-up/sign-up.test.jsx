import {fireEvent, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {redirectLoggedUserHomeUnlessInvite} from "../../app/route-guards.js";
import {apiService, ApiError, setLoggedUser} from "../../services/api.js";
import SignUp from "./sign-up.jsx";

const INVITE = {prescriberName: "Ana Lima", prescriberProfession: "Médica", expiresAt: "2026-12-01T10:00:00"};
const OUTDATED_TERM_MESSAGE = "O termo de consentimento foi atualizado. Leia a versão nova e aceite para continuar";

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

function renderSignUp(path = "/cadastro?convite=abc") {
    const router = createMemoryRouter([
        {path: "/cadastro", loader: redirectLoggedUserHomeUnlessInvite, element: <SignUp/>},
        {path: "/painel-paciente", element: <p>painel do paciente</p>},
    ], {initialEntries: [path]});
    render(<RouterProvider router={router}/>);
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

    // responsavel com dois filhos logado na conta do primeiro abre o convite do segundo (RF38)
    it("convite aberto com outra conta logada explica e deixa sair pra seguir o cadastro", async () => {
        const post = mockApi();
        const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue({status: 204, ok: true});
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        const user = userEvent.setup();
        renderSignUp();

        expect(await screen.findByText(/Você está conectado como/)).toHaveTextContent("Lucas Souza");
        expect(post).not.toHaveBeenCalled();
        await user.click(screen.getByRole("button", {name: "Sair e continuar o cadastro"}));

        expect(await screen.findByLabelText("Nome completo")).toBeInTheDocument();
        expect(fetchSpy).toHaveBeenCalledWith("/api/auth/logout", expect.objectContaining({method: "POST"}));
        expect(post).toHaveBeenCalledWith("/invites/lookup", {token: "abc"});
    });

    it("sem convite qm esta logado volta pra tela inicial", async () => {
        mockApi();
        setLoggedUser({id: 3, name: "Lucas Souza", role: "PATIENT", termPending: false});
        renderSignUp("/cadastro");

        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();
    });
});
