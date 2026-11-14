import {render, screen} from "@testing-library/react";
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
