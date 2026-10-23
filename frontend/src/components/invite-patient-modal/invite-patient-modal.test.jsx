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

    it("a lista de convites em aberto mostra a data e a hora em q cada um vence", async () => {
        vi.spyOn(apiService, "get").mockResolvedValue([
            {id: 1, createdAt: "2026-10-16T18:38:20.302023", expiresAt: "2026-10-23T18:38:20.302023"},
        ]);
        render(<InvitePatientModal show={true} onClose={() => {}}/>);

        expect(await screen.findByText(/Gerado em/)).toHaveTextContent(
            "Gerado em 16/10/2026 às 18:38, vale até 23/10/2026 às 18:38" + brasiliaTimeNote());
    });
});
