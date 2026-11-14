import {render, screen, waitFor} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import {brasiliaTimeNote} from "../../utils/date-format.js";
import InvitePatientModal from "./invite-patient-modal.jsx";

describe("InvitePatientModal", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // o link vence 7 dias dps, na msm hora em q foi gerado, e n no fim do dia
    it("o vale ate do link novo mostra a data e a hora", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([]);
        vi.spyOn(apiService, "post").mockResolvedValue({id: 1, token: "tok", expiresAt: "2026-10-23T18:38:20.302023"});
        const user = userEvent.setup();
        render(<InvitePatientModal show={true} onClose={() => {}}/>);

        await user.click(screen.getByRole("button", {name: "Gerar link de convite"}));

        const hint = await screen.findByText(/Vale até/);
        expect(hint).toHaveTextContent("Vale até 23/10/2026 às 18:38" + brasiliaTimeNote() + ".");
    });

    // o botao desabilitado enquanto gerava perdia o foco, q ia pro body (CT-39)
    it("gerar o link pelo teclado deixa o foco no botao enquanto a api responde", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([]);
        const post = vi.spyOn(apiService, "post").mockReturnValue(new Promise(() => {}));
        const user = userEvent.setup();
        render(<InvitePatientModal show={true} onClose={() => {}}/>);

        screen.getByRole("button", {name: "Gerar link de convite"}).focus();
        await user.keyboard("{Enter}");

        const creating = screen.getByRole("button", {name: "Gerando link..."});
        expect(creating).toHaveFocus();
        expect(creating).toBeEnabled();
        expect(creating).toHaveAttribute("aria-disabled", "true");
        await user.keyboard("{Enter}");
        expect(post).toHaveBeenCalledTimes(1);
    });

    // o botao de gerar some qnd o link chega e o foco ia pro body, sem o leitor de tela saber do link (CT-39)
    it("gerar o link pelo teclado leva o foco pro link gerado", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([]);
        vi.spyOn(apiService, "post")
            .mockResolvedValueOnce({id: 1, token: "tok1", expiresAt: "2026-11-21T10:00:00.000001"})
            .mockResolvedValueOnce({id: 2, token: "tok2", expiresAt: "2026-11-21T10:05:00.000001"});
        const user = userEvent.setup();
        render(<InvitePatientModal show={true} onClose={() => {}}/>);

        screen.getByRole("button", {name: "Gerar link de convite"}).focus();
        await user.keyboard("{Enter}");

        const link = await screen.findByLabelText("Link de convite");
        expect(link).toHaveValue(window.location.origin + "/cadastro?convite=tok1");
        expect(link).toHaveFocus();

        // o link de outro paciente tbm recebe o foco
        screen.getByRole("button", {name: /Gerar outro link/}).focus();
        await user.keyboard("{Enter}");
        await waitFor(() => expect(link).toHaveValue(window.location.origin + "/cadastro?convite=tok2"));
        expect(link).toHaveFocus();
    });

    // a linha cancelada some com o botao e o foco ia pro body, sem nada dizer q o convite foi cancelado (CT-39)
    it("cancelar um convite pelo teclado leva o foco pro aviso de cancelado", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([
            {id: 2, createdAt: "2026-11-13T12:36:28.121631", expiresAt: "2026-11-20T12:36:28.121631"},
            {id: 1, createdAt: "2026-11-12T09:10:05.000001", expiresAt: "2026-11-19T09:10:05.000001"},
        ]);
        vi.spyOn(apiService, "post").mockResolvedValue({id: 2, token: "tok", expiresAt: "2026-11-20T12:36:28.121631"});
        const put = vi.spyOn(apiService, "put").mockResolvedValue(null);
        const user = userEvent.setup();
        render(<InvitePatientModal show={true} onClose={() => {}}/>);
        await user.click(screen.getByRole("button", {name: "Gerar link de convite"}));

        // primeiro o outro convite: o link da tela continua
        (await screen.findByRole("button", {name: "Cancelar convite gerado em 12/11/2026 às 09:10"})).focus();
        await user.keyboard("{Enter}");

        // o aviso diz qual link foi cancelado, pq o "esse link" parecia o link da tela q continua valendo
        const notice = await screen.findByRole("status");
        expect(notice).toHaveTextContent("Convite cancelado. O link gerado em 12/11/2026 às 09:10 não serve mais "
            + "para criar a conta. O link de convite abaixo continua valendo.");
        expect(notice).toHaveFocus();
        expect(put).toHaveBeenCalledWith("/invites/1/cancel");
        expect(screen.getByLabelText("Link de convite")).toBeInTheDocument();

        // dps o link da tela, q some junto com a linha, e o foco vai de novo pro aviso
        screen.getByRole("button", {name: /o link acima$/}).focus();
        await user.keyboard("{Enter}");

        await waitFor(() => expect(screen.queryByLabelText("Link de convite")).not.toBeInTheDocument());
        expect(put).toHaveBeenCalledWith("/invites/2/cancel");
        expect(screen.getByRole("status")).toHaveTextContent("Convite cancelado. O link gerado em 13/11/2026 às 12:36 "
            + "não serve mais para criar a conta.");
        expect(screen.getByRole("status")).not.toHaveTextContent("continua valendo");
        expect(screen.getByRole("status")).toHaveFocus();
        expect(screen.getByRole("button", {name: "Gerar link de convite"})).toBeInTheDocument();
    });

    it("a lista de convites em aberto mostra a data e a hora em q cada um vence", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([
            {id: 1, createdAt: "2026-10-16T18:38:20.302023", expiresAt: "2026-10-23T18:38:20.302023"},
        ]);
        render(<InvitePatientModal show={true} onClose={() => {}}/>);

        expect(await screen.findByText(/Gerado em/)).toHaveTextContent(
            "Gerado em 16/10/2026 às 18:38, vale até 23/10/2026 às 18:38" + brasiliaTimeNote());
    });

    // dois convites do msm minuto ficavam iguais na lista e o prescritor n sabia qual cancelar (RN06)
    it("convites do mesmo minuto mostram os segundos e o link da tela vem marcado", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([
            {id: 2, createdAt: "2026-11-13T12:36:28.121631", expiresAt: "2026-11-20T12:36:28.121631"},
            {id: 1, createdAt: "2026-11-13T12:36:27.420803", expiresAt: "2026-11-20T12:36:27.420803"},
            {id: 3, createdAt: "2026-11-12T09:10:05.000001", expiresAt: "2026-11-19T09:10:05.000001"},
        ]);
        vi.spyOn(apiService, "post").mockResolvedValue({id: 2, token: "tok", expiresAt: "2026-11-20T12:36:28.121631"});
        const put = vi.spyOn(apiService, "put").mockResolvedValue(null);
        const user = userEvent.setup();
        render(<InvitePatientModal show={true} onClose={() => {}}/>);

        await user.click(screen.getByRole("button", {name: "Gerar link de convite"}));

        const rows = await screen.findAllByRole("listitem");
        expect(rows).toHaveLength(3);
        expect(rows[0]).toHaveTextContent("Gerado em 13/11/2026 às 12:36:28, vale até");
        expect(rows[0]).toHaveTextContent("É o link acima");
        expect(rows[1]).toHaveTextContent("Gerado em 13/11/2026 às 12:36:27, vale até");
        expect(rows[1]).not.toHaveTextContent("É o link acima");
        // o convite sozinho no minuto dele continua sem os segundos
        expect(rows[2]).toHaveTextContent("Gerado em 12/11/2026 às 09:10, vale até");

        expect(screen.getByRole("button", {name: "Cancelar convite gerado em 13/11/2026 às 12:36:28, o link acima"}))
            .toBeInTheDocument();
        await user.click(screen.getByRole("button", {name: "Cancelar convite gerado em 13/11/2026 às 12:36:27"}));
        expect(put).toHaveBeenCalledWith("/invites/1/cancel");
        // o link da tela continua la, agora sozinho no minuto dele
        expect(screen.getByLabelText("Link de convite")).toBeInTheDocument();
        expect(screen.getByRole("button", {name: "Cancelar convite gerado em 13/11/2026 às 12:36, o link acima"}))
            .toBeInTheDocument();
    });
});
