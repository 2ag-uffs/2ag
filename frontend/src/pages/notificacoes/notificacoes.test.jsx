import {render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import Notificacoes from "./notificacoes.jsx";

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
