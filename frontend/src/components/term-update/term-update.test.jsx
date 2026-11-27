import {render, screen, within} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import {declarationsOf, readCss} from "../../test/css-rules.js";
import TermUpdate from "./term-update.jsx";

function patientProfile(birthDate, researchConsent) {
    return {
        id: 3, role: "PATIENT", name: "Maria Souza", socialName: null, email: "maria@email.com", pendingEmail: null,
        cpf: "52998224725", birthDate: birthDate, phone: "49999887766", address: null,
        guardian: birthDate === "1980-01-01" ? null : {name: "Joana Souza", relationship: "mãe", phone: null},
        researchConsent: researchConsent, emailNotificationsEnabled: true,
    };
}

function mockTermAndProfile(profile) {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        if (path === "/profile") {
            return Promise.resolve(profile);
        }
        if (path === "/auth/me") {
            return Promise.resolve({id: 3, name: "Maria Souza", role: "PATIENT", termPending: true});
        }
        return Promise.resolve({version: "v2", text: "texto do termo novo", textHash: "hash"});
    });
}

const ACCEPTED = {active: true, acceptedAt: "2026-10-01T10:00:00", termChanged: false};
const NOT_ACCEPTED = {active: false, acceptedAt: null, termChanged: false};

// decisao de nov/2026 (D8): o perfil fica escondido com o termo pendente, e a pesquisa eh separada do termo
// do cadastro (RF37), entao a tela do termo mostra a pesquisa e deixa retirar o aceite dela sem aceitar o termo
describe("pesquisa na tela do termo pendente", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("mostra a situacao da pesquisa e deixa retirar o consentimento sem aceitar o termo", async () => {
        mockTermAndProfile(patientProfile("1980-01-01", ACCEPTED));
        const post = vi.spyOn(apiService, "post").mockResolvedValue(NOT_ACCEPTED);
        const onAccepted = vi.fn();
        const user = userEvent.setup();
        render(<TermUpdate onAccepted={onAccepted} onBirthDateFixed={vi.fn()}/>);

        const research = within(await screen.findByRole("region", {name: "Pesquisa"}));
        expect(research.getByText("Você aceitou participar da pesquisa em 01/10/2026. Pode retirar quando quiser."))
            .toBeInTheDocument();
        // entrar na pesquisa e ler o termo dela continuam so no perfil
        expect(research.getAllByRole("button")).toHaveLength(1);

        await user.click(research.getByRole("button", {name: "Retirar consentimento"}));

        expect(post).toHaveBeenCalledTimes(1);
        expect(post).toHaveBeenCalledWith("/consent-term/research/revoke", undefined);
        const revokedNotice = await research.findByText(
            "Consentimento retirado. Seus dados não entram em novas exportações para pesquisa.");
        expect(revokedNotice).toHaveFocus();
        expect(research.queryByRole("button", {name: "Retirar consentimento"})).not.toBeInTheDocument();
        expect(research.getByText(/Você não participa da pesquisa/)).toBeInTheDocument();
        // o termo continua pendente
        expect(screen.getByRole("button", {name: "Li e aceito"})).toBeInTheDocument();
        expect(onAccepted).not.toHaveBeenCalled();
    });

    it("quem n participa da pesquisa ve a situacao e nenhum botao da pesquisa", async () => {
        mockTermAndProfile(patientProfile("1980-01-01", NOT_ACCEPTED));
        render(<TermUpdate onAccepted={vi.fn()} onBirthDateFixed={vi.fn()}/>);

        const research = within(await screen.findByRole("region", {name: "Pesquisa"}));
        expect(research.getByText(/Você não participa da pesquisa\. Participar é opcional/)).toBeInTheDocument();
        expect(research.getByText(/A pesquisa fica no Perfil, depois de aceitar o termo acima\./)).toBeInTheDocument();
        expect(research.queryByRole("button")).not.toBeInTheDocument();
    });

    it("o erro ao retirar aparece e o botao continua", async () => {
        mockTermAndProfile(patientProfile("1980-01-01", ACCEPTED));
        vi.spyOn(apiService, "post").mockRejectedValue(new TypeError("Failed to fetch"));
        const user = userEvent.setup();
        render(<TermUpdate onAccepted={vi.fn()} onBirthDateFixed={vi.fn()}/>);

        const research = within(await screen.findByRole("region", {name: "Pesquisa"}));
        await user.click(research.getByRole("button", {name: "Retirar consentimento"}));

        expect(await research.findByRole("alert")).toBeInTheDocument();
        expect(research.getByRole("button", {name: "Retirar consentimento"})).toBeInTheDocument();
    });

    // a pesquisa ainda n abre pra menor de 18: sem aceite valendo a tela do termo n fala dela
    it("o menor sem aceite de pesquisa n ve a pesquisa", async () => {
        mockTermAndProfile(patientProfile((new Date().getFullYear() - 10) + "-01-01", NOT_ACCEPTED));
        render(<TermUpdate onAccepted={vi.fn()} onBirthDateFixed={vi.fn()}/>);

        expect(await screen.findByText("texto do termo novo")).toBeInTheDocument();
        expect(screen.queryByRole("region", {name: "Pesquisa"})).not.toBeInTheDocument();
    });

    // o aceite antigo do menor aparece pra retirar, e o aviso de retirado n some junto com o botao
    it("o menor com aceite antigo retira e continua vendo o aviso de retirado", async () => {
        mockTermAndProfile(patientProfile((new Date().getFullYear() - 10) + "-01-01", ACCEPTED));
        vi.spyOn(apiService, "post").mockResolvedValue(NOT_ACCEPTED);
        const user = userEvent.setup();
        render(<TermUpdate onAccepted={vi.fn()} onBirthDateFixed={vi.fn()}/>);

        const research = within(await screen.findByRole("region", {name: "Pesquisa"}));
        await user.click(research.getByRole("button", {name: "Retirar consentimento"}));

        expect(await research.findByText(/Consentimento retirado/)).toHaveFocus();
    });

    it("a pesquisa fica separada do aceite por um fio", () => {
        const research = declarationsOf(readCss(import.meta.url, "./term-update.module.css"), ".research");

        expect(research["border-top"]).toBe("1px solid var(--color-border)");
        expect(research["padding-top"]).toBe("var(--space-5)");
    });
});
