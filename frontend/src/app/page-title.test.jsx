import {render, screen, waitFor} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {useState} from "react";
import {createMemoryRouter, Link} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import TextField from "../components/form/text-field.jsx";
import Modal from "../components/modal/modal.jsx";
import RouteError from "../components/route-error/route-error.jsx";
import StatusPage from "../components/status-page/status-page.jsx";
import PageTitle from "./page-title.jsx";

// formulario de mentira: o envio com o nome em branco eh recusado com o erro no campo e com o nome passa
function NameForm() {
    const [name, setName] = useState("");
    const [error, setError] = useState(null);

    const handleSubmit = (event) => {
        event.preventDefault();
        setError(name ? null : "Informe o nome.");
    };

    return (
        <form onSubmit={handleSubmit}>
            <TextField label="Nome" name="name" value={name} error={error}
                       onChange={(event) => setName(event.target.value)}/>
            <button type="submit">Salvar</button>
        </form>
    );
}

function ProfilePage() {
    return (
        <>
            <NameForm/>
            <Link to="/agenda">Agenda</Link>
        </>
    );
}

// o formulario fica num modal por cima da lista
function PatientsPage() {
    const [isOpen, setIsOpen] = useState(false);
    return (
        <>
            <button type="button" onClick={() => setIsOpen(true)}>Novo paciente</button>
            <Modal show={isOpen} title="Novo paciente" onClickClose={() => setIsOpen(false)}>
                <NameForm/>
            </Modal>
        </>
    );
}

// como o layout faz com a tela de baixo qnd o termo ou o login abrem por cima
function CoveredPage() {
    const [isCovered, setIsCovered] = useState(false);
    return (
        <>
            <button type="button" onClick={() => setIsCovered(!isCovered)}>Cobrir a tela</button>
            <div hidden={isCovered}>
                <NameForm/>
            </div>
        </>
    );
}

// as rotas de mentira ficam embaixo da msm raiz q o router.jsx usa
function openAt(path) {
    const router = createMemoryRouter([
        {
            element: <PageTitle/>,
            children: [
                {path: "/perfil", handle: {title: "Perfil"}, element: <ProfilePage/>},
                {path: "/agenda", handle: {title: "Agenda"}, element: <p>agenda</p>},
                {path: "/pacientes", handle: {title: "Pacientes"}, element: <PatientsPage/>},
                {path: "/coberta", handle: {title: "Perfil"}, element: <CoveredPage/>},
                {path: "/sem-nome", element: <p>sem nome</p>},
                {
                    errorElement: <RouteError/>,
                    children: [
                        {
                            path: "/quebrada",
                            handle: {title: "Agenda"},
                            loader: () => {
                                throw new Error("resposta estranha da api");
                            },
                            element: <p>quebrada</p>,
                        },
                    ],
                },
                {path: "*", element: <StatusPage title="Página não encontrada" message="O endereço não existe."/>},
            ],
        },
    ], {initialEntries: [path]});
    render(<RouterProvider router={router}/>);
    return router;
}

async function expectTitle(title) {
    await waitFor(() => expect(document.title).toBe(title));
}

async function sendEmptyForm(user) {
    await user.click(screen.getByRole("button", {name: "Salvar"}));
    expect(screen.getByLabelText("Nome")).toHaveAttribute("aria-invalid", "true");
}

describe("PageTitle", () => {
    beforeEach(() => {
        document.title = "2AG";
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("a aba mostra o nome fixo da rota e troca junto com a tela", async () => {
        const router = openAt("/perfil");
        await expectTitle("Perfil · 2AG");

        await router.navigate("/agenda");
        await expectTitle("Agenda · 2AG");
    });

    it("rota sem nome fica so com o nome do sistema", async () => {
        const router = openAt("/agenda");
        await expectTitle("Agenda · 2AG");

        await router.navigate("/sem-nome");
        await expectTitle("2AG");
    });

    it("o envio recusado poe Erro na frente e o proximo envio aceito tira", async () => {
        const user = userEvent.setup();
        openAt("/perfil");
        await expectTitle("Perfil · 2AG");

        await sendEmptyForm(user);
        await expectTitle("Erro: Perfil · 2AG");

        // enquanto o erro esta no campo o titulo continua, mesmo com a pessoa ja digitando
        await user.type(screen.getByLabelText("Nome"), "Paciente Teste");
        expect(screen.getByLabelText("Nome")).toHaveAttribute("aria-invalid", "true");
        expect(document.title).toBe("Erro: Perfil · 2AG");

        await user.click(screen.getByRole("button", {name: "Salvar"}));
        await expectTitle("Perfil · 2AG");
    });

    it("trocar de tela com o erro no campo tira o Erro da frente", async () => {
        const user = userEvent.setup();
        openAt("/perfil");
        await sendEmptyForm(user);
        await expectTitle("Erro: Perfil · 2AG");

        await user.click(screen.getByRole("link", {name: "Agenda"}));
        await expectTitle("Agenda · 2AG");
    });

    it("fechar o modal com o erro no campo tira o Erro da frente", async () => {
        const user = userEvent.setup();
        openAt("/pacientes");
        await user.click(screen.getByRole("button", {name: "Novo paciente"}));
        await sendEmptyForm(user);
        await expectTitle("Erro: Pacientes · 2AG");

        await user.click(screen.getByRole("button", {name: "Fechar"}));
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
        await expectTitle("Pacientes · 2AG");
    });

    it("campo com erro na tela escondida n conta, e volta a contar qnd ela aparece de novo", async () => {
        const user = userEvent.setup();
        openAt("/coberta");
        await sendEmptyForm(user);
        await expectTitle("Erro: Perfil · 2AG");

        await user.click(screen.getByRole("button", {name: "Cobrir a tela"}));
        await expectTitle("Perfil · 2AG");

        await user.click(screen.getByRole("button", {name: "Cobrir a tela"}));
        await expectTitle("Erro: Perfil · 2AG");
    });

    it("a tela q n abriu troca o nome da rota pelo dela, e sair dela devolve o nome da rota", async () => {
        // o componente e o router escrevem o erro no console de proposito
        vi.spyOn(console, "error").mockImplementation(() => {});
        const router = openAt("/quebrada");
        await expectTitle("Não foi possível abrir esta tela · 2AG");

        await router.navigate("/agenda");
        await expectTitle("Agenda · 2AG");
    });

    it("endereco q n existe mostra pagina nao encontrada", async () => {
        openAt("/nao-existe");
        await expectTitle("Página não encontrada · 2AG");
    });

    // sem resposta da api as rotas nem montam, e a tela de sem conexao tbm tem nome
    it("a tela de sem conexao tem nome mesmo fora das rotas", async () => {
        render(<StatusPage title="Sem conexão com o sistema" message="Confira sua internet." showHomeLink={false}/>);
        await expectTitle("Sem conexão com o sistema · 2AG");
    });
});
