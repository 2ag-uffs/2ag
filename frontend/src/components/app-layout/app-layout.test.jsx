import {render, screen, within} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it} from "vitest";
import {setLoggedUser} from "../../services/api.js";
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
