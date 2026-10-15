import {act, fireEvent, render, screen, within} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, setLoggedUser} from "../../services/api.js";
import AppLayout from "./app-layout.jsx";

function openLayoutAt(path) {
    const router = createMemoryRouter([
        {element: <AppLayout/>, children: [{path: "*", element: <p>conteudo da tela</p>}]},
    ], {initialEntries: [path]});
    render(<RouterProvider router={router}/>);
}

function sideMenu() {
    return within(screen.getByRole("navigation", {name: "Menu principal"}));
}

function bottomMenu() {
    return within(screen.getByRole("navigation", {name: "Menu"}));
}

describe("AppLayout", () => {
    afterEach(() => {
        setLoggedUser(null);
        vi.restoreAllMocks();
    });

    it("paciente com termo antigo ve o aceite no lugar da tela e volta pra ela depois de aceitar", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({version: "v2", text: "texto do termo novo"});
        const post = vi.spyOn(apiService, "post")
            .mockResolvedValue({id: 3, name: "Maria Souza", role: "PATIENT", termPending: false});
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT", termPending: true});
        openLayoutAt("/painel-paciente");

        expect(await screen.findByText("texto do termo novo")).toBeInTheDocument();
        expect(screen.getByText("conteudo da tela")).not.toBeVisible();

        fireEvent.click(screen.getByRole("button", {name: "Li e aceito"}));

        expect(await screen.findByText("conteudo da tela")).toBeVisible();
        expect(post).toHaveBeenCalledWith("/consent-term/accept", {termVersion: "v2"});
    });

    it("o item Avisos mostra quantos avisos n foram lidos e acompanha a tela de avisos", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({unread: 3});
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT", termPending: false});
        openLayoutAt("/painel-paciente");

        expect(await sideMenu().findByRole("link", {name: "Avisos, 3 não lidos"})).toBeInTheDocument();
        expect(bottomMenu().getByRole("link", {name: "Avisos, 3 não lidos"})).toHaveTextContent("3");

        act(() => {
            window.dispatchEvent(new CustomEvent("notifications-changed", {detail: 0}));
        });

        expect(await sideMenu().findByRole("link", {name: "Avisos"})).toBeInTheDocument();
    });

    // a versao mudou com a sessao aberta: o termo abre por cima e a tela fica guardada embaixo
    it("o aviso de termo pendente da api abre o aceite sem desmontar a tela", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({version: "v3", text: "texto do termo mais novo"});
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT", termPending: false});
        openLayoutAt("/painel-paciente");
        expect(await screen.findByText("conteudo da tela")).toBeVisible();

        act(() => {
            window.dispatchEvent(new Event("term-pending"));
        });

        expect(await screen.findByText("texto do termo mais novo")).toBeInTheDocument();
        expect(screen.getByText("conteudo da tela")).not.toBeVisible();
    });

    it("no progresso de um paciente o menu lateral marca Progresso e n Pacientes", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        openLayoutAt("/paciente/5/progresso");

        expect(await screen.findByText("conteudo da tela")).toBeInTheDocument();
        expect(sideMenu().getByRole("link", {name: "Progresso"})).toHaveAttribute("aria-current", "page");
        expect(sideMenu().getByRole("link", {name: "Pacientes"})).not.toHaveAttribute("aria-current");
    });

    it("no celular o progresso de um paciente marca Pacientes pq Progresso n esta na barra", async () => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        openLayoutAt("/paciente/5/progresso");

        await screen.findByText("conteudo da tela");
        expect(bottomMenu().getByRole("link", {name: "Pacientes"})).toHaveAttribute("aria-current", "page");
    });

    it("tela dentro de um item continua marcando o item pelo prefixo", async () => {
        setLoggedUser({id: 3, name: "Maria Souza", role: "PATIENT"});
        openLayoutAt("/escalas/diario-sono");

        await screen.findByText("conteudo da tela");
        expect(sideMenu().getByRole("link", {name: "Escalas"})).toHaveAttribute("aria-current", "page");
    });

    it("administrador tem o Perfil no menu lateral e na barra do celular", async () => {
        setLoggedUser({id: 1, name: "Administrador", role: "ADMIN"});
        openLayoutAt("/perfil");

        await screen.findByText("conteudo da tela");
        expect(sideMenu().getByRole("link", {name: "Perfil"})).toHaveAttribute("aria-current", "page");
        expect(bottomMenu().getByRole("link", {name: "Perfil"})).toHaveAttribute("aria-current", "page");
        expect(sideMenu().getByRole("link", {name: "Prescritores"})).not.toHaveAttribute("aria-current");
    });

    it("na tela de contas de paciente o menu do administrador marca Pacientes e n Prescritores", async () => {
        setLoggedUser({id: 1, name: "Administrador", role: "ADMIN"});
        openLayoutAt("/administracao/pacientes");

        await screen.findByText("conteudo da tela");
        expect(sideMenu().getByRole("link", {name: "Pacientes"})).toHaveAttribute("aria-current", "page");
        expect(sideMenu().getByRole("link", {name: "Prescritores"})).not.toHaveAttribute("aria-current");
    });
});
