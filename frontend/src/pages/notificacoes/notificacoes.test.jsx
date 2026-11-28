import {act, render, screen, waitFor, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService, ApiError} from "../../services/api.js";
import {contrastOf, rgbaOf, themesOf} from "../../test/contrast.js";
import {declarationsOf, mediaRules, readCss} from "../../test/css-rules.js";
import Notificacoes from "./notificacoes.jsx";

const noticesCss = readCss(import.meta.url, "./notificacoes.module.css");
const THEMES = themesOf(readCss(import.meta.url, "../../styles/colors.css"));

// o valor do css, q aqui eh sempre var(--token), na cor do tema
function colorIn(theme, value) {
    return rgbaOf(theme[value.match(/var\((--[\w-]+)\)/)[1]]);
}

function notification(id) {
    return {
        id, title: "Aviso " + id, message: "Texto " + id, type: "ALERT", link: null, isRead: true,
        createdAt: "2026-10-10T10:00:00",
    };
}

const FIRST_PAGE = Array.from({length: 20}, (item, index) => notification(index + 1));

describe("Notificacoes", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // pagina alem da ultima vem vazia da api, com totalPages ja menor
    it("apagar o unico aviso da ultima pagina volta pra pagina anterior", async () => {
        let total = 21;
        vi.spyOn(apiService, "get").mockImplementation(async (path) => {
            const pageNumber = Number(path.split("page=")[1]);
            const content = pageNumber === 0 ? FIRST_PAGE : (total > 20 ? [notification(21)] : []);
            return {
                notifications: content, page: pageNumber, totalPages: Math.ceil(total / 20),
                totalNotifications: total, unread: 0,
            };
        });
        vi.spyOn(apiService, "delete").mockImplementation(async () => {
            total = 20;
            return null;
        });
        const user = userEvent.setup();
        const router = createMemoryRouter([{path: "/notificacoes", element: <Notificacoes/>}],
            {initialEntries: ["/notificacoes"]});
        render(<RouterProvider router={router}/>);

        await user.click(await screen.findByRole("button", {name: "Próxima"}));
        expect(await screen.findByText("Aviso 21")).toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Apagar"}));

        expect(await screen.findByText("Aviso 1")).toBeInTheDocument();
        expect(screen.queryByText("Aviso 21")).not.toBeInTheDocument();
        expect(screen.queryByText("Nenhum aviso por aqui.")).not.toBeInTheDocument();
    });

    // dois toques seguidos no Proxima pedem duas paginas, e a resposta da primeira pode chegar por ultimo:
    // ela ficava na tela no lugar da pagina q a pessoa pediu
    it("a pagina pedida por ultimo fica na tela msm qnd a resposta da anterior chega dps", async () => {
        const deliverPage = {};
        vi.spyOn(apiService, "get").mockImplementation((path) => {
            const pageNumber = Number(path.split("page=")[1]);
            const pageBody = {
                notifications: pageNumber === 0 ? FIRST_PAGE : [notification(pageNumber * 20 + 1)],
                page: pageNumber, totalPages: 4, totalNotifications: 61, unread: 0,
            };
            if (pageNumber === 0) {
                return Promise.resolve(pageBody);
            }
            return new Promise((resolve) => {
                deliverPage[pageNumber] = () => resolve(pageBody);
            });
        });
        const user = userEvent.setup();
        const router = createMemoryRouter([{path: "/notificacoes", element: <Notificacoes/>}],
            {initialEntries: ["/notificacoes"]});
        render(<RouterProvider router={router}/>);

        await user.click(await screen.findByRole("button", {name: "Próxima"}));
        await user.click(screen.getByRole("button", {name: "Próxima"}));
        await act(async () => deliverPage[2]());
        await act(async () => deliverPage[1]());

        expect(await screen.findByText("Aviso 41")).toBeInTheDocument();
        expect(screen.getByText("Página 3 de 4")).toBeInTheDocument();
        expect(screen.queryByText("Aviso 21")).not.toBeInTheDocument();
    });

    // o aviso continua na lista ate a recarga chegar, e o segundo toque mandava outro DELETE, q a api recusa
    // pq o aviso ja foi apagado: a tela mostrava erro de uma acao q deu certo
    it("dois toques seguidos em Apagar mandam um pedido so e n mostram erro", async () => {
        let notifications = [notification(1), notification(2)];
        vi.spyOn(apiService, "get").mockImplementation(async () => ({
            notifications, page: 0, totalPages: 1, totalNotifications: notifications.length, unread: 0,
        }));
        let finishDelete = null;
        const remove = vi.spyOn(apiService, "delete").mockImplementation(() => {
            if (finishDelete) {
                return Promise.reject(new ApiError(404, "Não foi possível encontrar o que você procura."));
            }
            return new Promise((resolve) => {
                finishDelete = () => {
                    notifications = [notification(2)];
                    resolve(null);
                };
            });
        });
        const user = userEvent.setup();
        const router = createMemoryRouter([{path: "/notificacoes", element: <Notificacoes/>}],
            {initialEntries: ["/notificacoes"]});
        render(<RouterProvider router={router}/>);

        await screen.findByText("Aviso 1");
        const firstItem = screen.getByRole("heading", {name: "Aviso 1"}).closest("li");
        await user.click(within(firstItem).getByRole("button", {name: "Apagar"}));
        // enquanto a api responde a linha fica ocupada e os dois botoes dela n aceitam outro toque
        expect(firstItem).toHaveAttribute("aria-busy", "true");
        expect(within(firstItem).getByRole("button", {name: "Apagar"})).toHaveAttribute("aria-disabled", "true");
        expect(within(firstItem).getByRole("button", {name: "Marcar como lido"})).toHaveAttribute("aria-disabled", "true");
        await user.click(within(firstItem).getByRole("button", {name: "Apagar"}));
        await user.click(within(firstItem).getByRole("button", {name: "Marcar como lido"}));
        await act(async () => finishDelete());

        await waitFor(() => expect(screen.queryByText("Aviso 1")).not.toBeInTheDocument());
        expect(remove).toHaveBeenCalledTimes(1);
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        // a linha q ficou n herda o ocupado
        const secondItem = screen.getByRole("heading", {name: "Aviso 2"}).closest("li");
        expect(secondItem).not.toHaveAttribute("aria-busy");
        expect(within(secondItem).getByRole("button", {name: "Apagar"})).not.toHaveAttribute("aria-disabled");
    });

    // a recarga pedida pelo apagar de um aviso soltava todas as linhas, e o outro aviso q ainda estava com o
    // DELETE no ar aceitava o segundo toque
    it("a recarga de um aviso apagado n solta a linha de outro aviso q ainda esta sendo apagado", async () => {
        let notifications = [notification(1), notification(2)];
        let loads = 0;
        let deliverReload = null;
        vi.spyOn(apiService, "get").mockImplementation(() => {
            loads += 1;
            const pageBody = {
                notifications, page: 0, totalPages: 1, totalNotifications: notifications.length, unread: 0,
            };
            if (loads !== 2) {
                return Promise.resolve(pageBody);
            }
            return new Promise((resolve) => {
                deliverReload = () => resolve(pageBody);
            });
        });
        let finishSecondDelete = null;
        const remove = vi.spyOn(apiService, "delete").mockImplementation((path) => {
            if (path.endsWith("/1")) {
                notifications = [notification(2)];
                return Promise.resolve(null);
            }
            return new Promise((resolve) => {
                finishSecondDelete = () => {
                    notifications = [];
                    resolve(null);
                };
            });
        });
        const user = userEvent.setup();
        const router = createMemoryRouter([{path: "/notificacoes", element: <Notificacoes/>}],
            {initialEntries: ["/notificacoes"]});
        render(<RouterProvider router={router}/>);

        await screen.findByText("Aviso 1");
        const firstItem = screen.getByRole("heading", {name: "Aviso 1"}).closest("li");
        await user.click(within(firstItem).getByRole("button", {name: "Apagar"}));
        await waitFor(() => expect(deliverReload).not.toBeNull());
        const secondItem = screen.getByRole("heading", {name: "Aviso 2"}).closest("li");
        await user.click(within(secondItem).getByRole("button", {name: "Apagar"}));
        await act(async () => deliverReload());

        await waitFor(() => expect(screen.queryByText("Aviso 1")).not.toBeInTheDocument());
        const stillBusyItem = screen.getByRole("heading", {name: "Aviso 2"}).closest("li");
        expect(stillBusyItem).toHaveAttribute("aria-busy", "true");
        await user.click(within(stillBusyItem).getByRole("button", {name: "Apagar"}));
        expect(remove).toHaveBeenCalledTimes(2);

        await act(async () => finishSecondDelete());
        expect(await screen.findByText("Nenhum aviso por aqui.")).toBeInTheDocument();
        expect(remove).toHaveBeenCalledTimes(2);
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    // o botao tocado some com o aviso e o foco caia no body: com o leitor de tela a pessoa voltava pro comeco da pagina
    describe("foco dps de apagar e de marcar todos como lidos", () => {
        // a lista q a api devolve agora, trocada pelo teste depois de cada acao
        let current;

        function renderList(notifications, unread = 0) {
            current = {notifications, unread};
            vi.spyOn(apiService, "get").mockImplementation(async () => ({
                notifications: current.notifications, page: 0, totalPages: 1,
                totalNotifications: current.notifications.length, unread: current.unread,
            }));
            const router = createMemoryRouter([{path: "/notificacoes", element: <Notificacoes/>}],
                {initialEntries: ["/notificacoes"]});
            render(<RouterProvider router={router}/>);
        }

        function deleteRemoves(id) {
            vi.spyOn(apiService, "delete").mockImplementation(async () => {
                current = {...current, notifications: current.notifications.filter((item) => item.id !== id)};
                return null;
            });
        }

        function itemOf(title) {
            return screen.getByRole("heading", {name: title}).closest("li");
        }

        it("apagar um aviso leva o foco pro Abrir do aviso q ficou no lugar dele", async () => {
            renderList([notification(1), notification(2), {...notification(3), link: "/progresso"}]);
            deleteRemoves(2);
            const user = userEvent.setup();

            await screen.findByText("Aviso 2");
            await user.click(within(itemOf("Aviso 2")).getByRole("button", {name: "Apagar"}));

            await waitFor(() => expect(screen.queryByText("Aviso 2")).not.toBeInTheDocument());
            await waitFor(() => expect(within(itemOf("Aviso 3")).getByRole("button", {name: "Abrir"})).toHaveFocus());
        });

        it("apagar o ultimo da lista leva o foco pro primeiro botao do aviso de cima", async () => {
            renderList([notification(1), notification(2)]);
            deleteRemoves(2);
            const user = userEvent.setup();

            await screen.findByText("Aviso 2");
            await user.click(within(itemOf("Aviso 2")).getByRole("button", {name: "Apagar"}));

            await waitFor(() => expect(within(itemOf("Aviso 1")).getByRole("button", {name: "Marcar como lido"}))
                .toHaveFocus());
        });

        it("apagar o unico aviso leva o foco pro titulo da tela", async () => {
            renderList([notification(1)]);
            deleteRemoves(1);
            const user = userEvent.setup();

            await user.click(await screen.findByRole("button", {name: "Apagar"}));

            expect(await screen.findByText("Nenhum aviso por aqui.")).toBeInTheDocument();
            await waitFor(() => expect(screen.getByRole("heading", {name: "Avisos"})).toHaveFocus());
        });

        it("marcar todos como lidos leva o foco pra contagem de avisos", async () => {
            renderList([{...notification(1), isRead: false}, {...notification(2), isRead: false}], 2);
            vi.spyOn(apiService, "post").mockImplementation(async () => {
                current = {notifications: current.notifications.map((item) => ({...item, isRead: true})), unread: 0};
                return null;
            });
            const user = userEvent.setup();

            await user.click(await screen.findByRole("button", {name: "Marcar todos como lidos"}));

            const count = await screen.findByText("Você está em dia, nenhum aviso novo.");
            await waitFor(() => expect(count).toHaveFocus());
            expect(screen.queryByRole("button", {name: "Marcar todos como lidos"})).not.toBeInTheDocument();
        });
    });

    // sem rede o aviso de erro ficava la no alto, fora da tela de quem tinha rolado ate o aviso q queria apagar
    it("apagar sem conexao leva o foco pro aviso de erro", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({
            notifications: [notification(1), notification(2)], page: 0, totalPages: 1, totalNotifications: 2, unread: 0,
        });
        vi.spyOn(apiService, "delete").mockRejectedValue(new TypeError("Failed to fetch"));
        const user = userEvent.setup();
        const router = createMemoryRouter([{path: "/notificacoes", element: <Notificacoes/>}],
            {initialEntries: ["/notificacoes"]});
        render(<RouterProvider router={router}/>);

        await screen.findByText("Aviso 2");
        await user.click(screen.getAllByRole("button", {name: "Apagar"})[1]);

        const alert = await screen.findByRole("alert");
        expect(alert).toHaveTextContent("Não foi possível conectar ao sistema. Confira sua internet e tente de novo.");
        await waitFor(() => expect(alert).toHaveFocus());
        expect(screen.getAllByRole("alert")).toHaveLength(1);
        expect(screen.getByText("Aviso 2")).toBeInTheDocument();
    });

    // o marcar como lido demora, a pessoa toca em outra tela, e a resposta q chegava dps levava ela pro link do aviso
    it("a resposta do Abrir q chega dps de a pessoa sair da tela n troca de tela", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({
            notifications: [{...notification(1), isRead: false, link: "/agendamento-consulta"}],
            page: 0, totalPages: 1, totalNotifications: 1, unread: 1,
        });
        let finishRead = null;
        const post = vi.spyOn(apiService, "post").mockImplementation(() => new Promise((resolve) => {
            finishRead = () => resolve(null);
        }));
        const user = userEvent.setup();
        const router = createMemoryRouter([
            {path: "/notificacoes", element: <Notificacoes/>},
            {path: "/agendamento-consulta", element: <p>tela de consultas</p>},
            {path: "/painel-paciente", element: <p>painel do paciente</p>},
        ], {initialEntries: ["/notificacoes"]});
        render(<RouterProvider router={router}/>);

        await user.click(await screen.findByRole("button", {name: "Abrir"}));
        await waitFor(() => expect(post).toHaveBeenCalledWith("/notifications/1/read"));
        await act(() => router.navigate("/painel-paciente"));
        expect(await screen.findByText("painel do paciente")).toBeInTheDocument();

        await act(async () => finishRead());

        expect(router.state.location.pathname).toBe("/painel-paciente");
        expect(screen.queryByText("tela de consultas")).not.toBeInTheDocument();
    });

    // apagar n pede confirmacao: fica longe do Abrir, na outra ponta no celular, e na cor de acao q perde algo
    it("o Apagar fica longe do Abrir e na terracota, q passa de 4,5:1 nos temas", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue({
            notifications: [notification(1)], page: 0, totalPages: 1, totalNotifications: 1, unread: 0,
        });
        const router = createMemoryRouter([{path: "/notificacoes", element: <Notificacoes/>}],
            {initialEntries: ["/notificacoes"]});
        render(<RouterProvider router={router}/>);

        const removeButton = await screen.findByRole("button", {name: "Apagar"});
        expect(removeButton).toHaveClass("button-tertiary", "button-small");
        expect(removeButton.className).toMatch(/remove/);

        const remove = declarationsOf(noticesCss, ".remove");
        const hover = declarationsOf(noticesCss, ".remove:hover");
        const card = declarationsOf(noticesCss, ".item");
        expect(remove.color).toBe("var(--color-error)");
        Object.entries(THEMES).forEach(([themeName, theme]) => {
            const text = colorIn(theme, remove.color);
            expect(contrastOf(text, colorIn(theme, card["background-color"])), themeName).toBeGreaterThanOrEqual(4.5);
            expect(contrastOf(text, colorIn(theme, hover["background-color"])), themeName).toBeGreaterThanOrEqual(4.5);
        });
        expect(declarationsOf(noticesCss, ".actions").gap).toBe("var(--space-4)");
        expect(declarationsOf(mediaRules(noticesCss, "@media (max-width: 47.99rem)"), ".actions")["justify-content"])
            .toBe("space-between");
    });

    // o texto pedia 16rem do lado do icone e o cartao tem uns 294 px por dentro no celular de 360: cada aviso
    // virava tres linhas, com o icone sozinho em cima. no de 320 sobram 202 px do lado do icone, e 12rem cabe.
    // com flex-basis 0 o texto ficava com uns 90 px do lado do icone na letra em 200%
    it("no celular o texto do aviso fica do lado do icone, e no computador nada muda", () => {
        const mobile = mediaRules(noticesCss, "@media (max-width: 47.99rem)");

        expect(declarationsOf(mobile, ".content")["flex-basis"]).toBe("12rem");
        expect(declarationsOf(noticesCss, ".content").flex).toBe("1 1 16rem");
        // o Abrir comeca onde o texto comeca: a largura do icone mais o espaco entre as colunas do cartao
        const columnGap = declarationsOf(noticesCss, ".item").gap.split(" ")[1];
        expect(declarationsOf(mobile, ".actions")["padding-left"])
            .toBe("calc(" + declarationsOf(noticesCss, ".icon").width + " + " + columnGap + ")");
    });

    // o aviso da consulta vem da api com o horario de brasilia no meio do texto
    describe("aviso com horario de consulta", () => {
        const APPOINTMENT_NOTICE = {
            id: 1, title: "Consulta marcada", message: "Sua consulta com Dra Ana foi marcada para 20/10/2026 às 10:00.",
            type: "APPOINTMENT", link: null, isRead: true, createdAt: "2026-10-16T09:00:00",
        };

        function renderNotices() {
            vi.spyOn(apiService, "get").mockResolvedValue({
                notifications: [APPOINTMENT_NOTICE], page: 0, totalPages: 1, totalNotifications: 1, unread: 0,
            });
            const router = createMemoryRouter([{path: "/notificacoes", element: <Notificacoes/>}],
                {initialEntries: ["/notificacoes"]});
            render(<RouterProvider router={router}/>);
        }

        afterEach(() => {
            vi.unstubAllEnvs();
        });

        it("fora do fuso de brasilia diz q o horario eh de brasilia", async () => {
            vi.stubEnv("TZ", "America/Manaus");
            renderNotices();

            expect(await screen.findByText(
                "Sua consulta com Dra Ana foi marcada para 20/10/2026 às 10:00 (horário de Brasília).")).toBeInTheDocument();
        });

        it("no fuso de brasilia fica como a api mandou", async () => {
            vi.stubEnv("TZ", "America/Sao_Paulo");
            renderNotices();

            expect(await screen.findByText("Sua consulta com Dra Ana foi marcada para 20/10/2026 às 10:00."))
                .toBeInTheDocument();
            expect(screen.queryByText(/horário de Brasília/)).not.toBeInTheDocument();
        });
    });
});
