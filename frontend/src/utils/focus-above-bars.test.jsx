import {act, render, screen, waitFor, within} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import AppLayout from "../components/app-layout/app-layout.jsx";
import FormSection, {FormActions} from "../components/form-section/form-section.jsx";
import ScaleForm from "../components/scale-form/scale-form.jsx";
import {apiService, setLoggedUser} from "../services/api.js";

// o jsdom n desenha a tela, entao cada caixa recebe a posicao q o navegador mediu no reteste do CT-39

function box(top, bottom, left, right) {
    return {top, bottom, left, right, height: bottom - top, width: right - left, x: left, y: top};
}

function placeAt(element, rect) {
    vi.spyOn(element, "getBoundingClientRect").mockReturnValue(rect);
}

// formulario comprido com a barra de acoes presa no pe da tela, como o registro da consulta
function LongForm() {
    return (
        <form>
            <FormSection title="Conduta">
                <label htmlFor="plan">Conduta e plano terapêutico</label>
                <textarea id="plan"/>
                <label htmlFor="exams">Exames complementares</label>
                <textarea id="exams"/>
            </FormSection>
            <FormActions>
                <button type="button">Cancelar</button>
                <button type="submit">Salvar consulta</button>
            </FormActions>
        </form>
    );
}

function renderForm() {
    const router = createMemoryRouter([
        {element: <AppLayout/>, children: [{path: "/consulta/2/registro", element: <LongForm/>}]},
    ], {initialEntries: ["/consulta/2/registro"]});
    render(<RouterProvider router={router}/>);
}

// escala com o contador preso no alto do formulario
function ScaleFormPage() {
    const items = [{key: "dor", label: "Dor", type: "NOTA", minValue: 0, maxValue: 10, lowAnchor: "sem dor",
        highAnchor: "pior dor"}, {key: "comentario", label: "Observações", type: "TEXTO"}];
    return (
        <form aria-label="Escala">
            <ScaleForm items={items} values={{}} onChange={() => {}}/>
        </form>
    );
}

function renderScale() {
    const router = createMemoryRouter([
        {element: <AppLayout/>, children: [{path: "/escalas/registro-dor", element: <ScaleFormPage/>}]},
    ], {initialEntries: ["/escalas/registro-dor"]});
    render(<RouterProvider router={router}/>);
}

// pergunta de opcoes em cartao com a barra de acoes no pe, como o pittsburgh do paciente
function ScaleCardsPage() {
    const items = [{key: "freqAcordouNoite", label: "Acordou no meio da noite ou de manhã cedo", type: "ESCOLHA",
        options: [{value: 0, label: "Nenhuma no último mês"}, {value: 1, label: "Menos de uma vez por semana"}]}];
    return (
        <form aria-label="Escala">
            <ScaleForm items={items} values={{}} onChange={() => {}}/>
            <FormActions>
                <button type="button">Voltar</button>
                <button type="submit">Salvar respostas</button>
            </FormActions>
        </form>
    );
}

function renderScaleCards() {
    const router = createMemoryRouter([
        {element: <AppLayout/>, children: [{path: "/escalas/pittsburgh", element: <ScaleCardsPage/>}]},
    ], {initialEntries: ["/escalas/pittsburgh"]});
    render(<RouterProvider router={router}/>);
}

// a barra de acoes eh a div em volta dos botoes do fim do formulario
function actionsBar() {
    return screen.getByRole("button", {name: "Salvar consulta"}).parentElement;
}

// espera o quadro em q a tela confere o campo com foco
function nextFrame() {
    return act(() => new Promise((resolve) => requestAnimationFrame(resolve)));
}

describe("campo com foco atras da barra presa no pe da tela", () => {
    let scrollBy;
    let windowHeight;

    beforeEach(() => {
        setLoggedUser({id: 2, name: "Ana Lima", role: "PRESCRIBER"});
        vi.spyOn(apiService, "get").mockResolvedValue({unread: 0});
        scrollBy = vi.spyOn(window, "scrollBy").mockImplementation(() => {});
        windowHeight = window.innerHeight;
    });

    afterEach(() => {
        vi.restoreAllMocks();
        setLoggedUser(null);
        window.innerHeight = windowHeight;
    });

    // em 1280x900 o campo ia de 769 a 891 com a barra comecando em 816: so 47 dos 122 px ficavam a vista
    // o navegador n rola pq o campo ja esta dentro da janela, entao a tela sobe o q falta mais uma folga
    it("no computador a pagina sobe so o bastante pro campo sair de baixo da barra de acoes", async () => {
        window.innerHeight = 900;
        renderForm();
        const plan = await screen.findByLabelText("Conduta e plano terapêutico");
        placeAt(actionsBar(), box(816, 900, 248, 1280));
        placeAt(plan, box(769, 891, 280, 1240));

        act(() => plan.focus());

        await waitFor(() => expect(scrollBy).toHaveBeenCalledTimes(1));
        expect(scrollBy).toHaveBeenCalledWith(0, 891 + 12 - 816);
    });

    // no celular a barra de acoes para em cima do menu de baixo, e o campo pode estar atras do menu
    it("no celular desconta o menu de baixo e a barra de acoes q fica em cima dele", async () => {
        window.innerHeight = 667;
        renderForm();
        const exams = await screen.findByLabelText("Exames complementares");
        placeAt(screen.getByRole("navigation", {name: "Menu"}), box(602, 667, 0, 375));
        placeAt(actionsBar(), box(512, 603, 16, 359));
        placeAt(exams, box(610, 654, 16, 359));

        act(() => exams.focus());

        await waitFor(() => expect(scrollBy).toHaveBeenCalledTimes(1));
        expect(scrollBy).toHaveBeenCalledWith(0, 654 + 12 - 512);
    });

    it("campo inteiro acima da barra fica onde esta", async () => {
        window.innerHeight = 900;
        renderForm();
        const plan = await screen.findByLabelText("Conduta e plano terapêutico");
        placeAt(actionsBar(), box(816, 900, 248, 1280));
        placeAt(plan, box(500, 622, 280, 1240));

        act(() => plan.focus());
        await nextFrame();

        expect(scrollBy).not.toHaveBeenCalled();
    });

    // a lateral fica parada do lado do formulario, entao a barra de acoes n cobre o Sair dela
    // e os botoes da propria barra n tem como sair de baixo dela
    it("a lateral e os botoes da propria barra n fazem a pagina rolar", async () => {
        window.innerHeight = 900;
        renderForm();
        await screen.findByLabelText("Conduta e plano terapêutico");
        placeAt(actionsBar(), box(816, 900, 248, 1280));
        const sidebar = within(screen.getByRole("complementary"));
        const logout = sidebar.getByRole("button", {name: "Sair"});
        placeAt(logout, box(840, 880, 190, 230));
        const save = screen.getByRole("button", {name: "Salvar consulta"});
        placeAt(save, box(840, 884, 1100, 1240));

        act(() => logout.focus());
        await nextFrame();
        act(() => save.focus());
        await nextFrame();

        expect(scrollBy).not.toHaveBeenCalled();
    });

    // o contador das escalas fica preso no alto e o campo q o shift+tab alcanca podia parar atras dele
    it("campo atras do contador preso no alto desce so o bastante pra aparecer", async () => {
        window.innerHeight = 900;
        renderScale();
        const notes = await screen.findByLabelText("Observações");
        const counter = within(screen.getByRole("form", {name: "Escala"})).getByRole("status").closest("[data-top-bar]");
        placeAt(counter, box(0, 52, 248, 1280));
        placeAt(notes, box(20, 100, 280, 1240));

        act(() => notes.focus());

        await waitFor(() => expect(scrollBy).toHaveBeenCalledTimes(1));
        expect(scrollBy).toHaveBeenCalledWith(0, 20 - 52 - 12);
    });

    // o contador ainda no lugar dele fica em cima dos itens e n cobre nada
    it("contador fora do alto da tela n mexe na pagina", async () => {
        window.innerHeight = 900;
        renderScale();
        const notes = await screen.findByLabelText("Observações");
        const counter = within(screen.getByRole("form", {name: "Escala"})).getByRole("status").closest("[data-top-bar]");
        placeAt(counter, box(300, 352, 248, 1280));
        placeAt(notes, box(400, 480, 280, 1240));

        act(() => notes.focus());
        await nextFrame();

        expect(scrollBy).not.toHaveBeenCalled();
    });

    // o radio de vdd tem 1 px e fica no alto do cartao, entao a conta vale pro cartao inteiro (o label em volta)
    // em 768x1024 o cartao ia de 918 a 968 com a barra comecando em 940, e o anel de foco sumia embaixo dela
    it("a opcao em cartao sobe ate o cartao inteiro sair de baixo da barra de acoes", async () => {
        window.innerHeight = 1024;
        renderScaleCards();
        const option = await screen.findByRole("radio", {name: "Nenhuma no último mês"});
        placeAt(screen.getByRole("button", {name: "Salvar respostas"}).parentElement, box(940, 1024, 0, 768));
        placeAt(option.closest("label"), box(918, 968, 40, 728));
        placeAt(option, box(918, 919, 40, 41));

        act(() => option.focus());

        await waitFor(() => expect(scrollBy).toHaveBeenCalledTimes(1));
        expect(scrollBy).toHaveBeenCalledWith(0, 968 + 12 - 940);
    });

    // campo mais alto q o espaco livre: sobe so ate o topo dele, onde fica o rotulo e o cursor
    it("campo mais alto q o espaco livre sobe so ate o topo dele aparecer", async () => {
        window.innerHeight = 900;
        renderForm();
        const plan = await screen.findByLabelText("Conduta e plano terapêutico");
        placeAt(actionsBar(), box(816, 900, 248, 1280));
        placeAt(plan, box(300, 1200, 280, 1240));

        act(() => plan.focus());

        await waitFor(() => expect(scrollBy).toHaveBeenCalledTimes(1));
        expect(scrollBy).toHaveBeenCalledWith(0, 300 - 12);
    });
});
