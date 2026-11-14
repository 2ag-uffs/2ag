import {readFileSync} from "node:fs";
import {render, screen, within} from "@testing-library/react";
import {createMemoryRouter} from "react-router";
import {RouterProvider} from "react-router/dom";
import {afterEach, describe, expect, it, vi} from "vitest";
import {apiService} from "../../services/api.js";
import {addDays, formatDate, toIsoDate} from "../../utils/date-format.js";
import Impressao from "./impressao.jsx";

const PATIENT = {id: 5, name: "Maria Souza", birthDate: "1990-01-01", prescriberName: "Ana Lima", archived: false};

const APPOINTMENT = {
    id: 7, dateTime: toIsoDate(addDays(new Date(), -8)) + "T10:00:00", modality: "PRESENCIAL",
    status: "CONCLUIDA", annulled: false, prescriberName: "Ana Lima", weight: 72.5,
    evolution: "Paciente relata melhora parcial.",
};

const ANAMNESIS = {id: 3, assessmentDate: toIsoDate(addDays(new Date(), -5)), annulled: false, reasonForVisit: "Dor"};

// receita emitida ha tantos dias, em uso ou substituida
function prescriptionOf(id, daysAgo, current, productDescription) {
    return {
        id, current, annulled: false, status: current ? "VIGENTE" : "SUBSTITUIDA",
        productDescription, posology: "2 gotas à noite", components: [], escalationSteps: [],
        appointmentDateTime: toIsoDate(addDays(new Date(), -daysAgo)) + "T09:00:00",
        prescriberName: "Ana Lima",
    };
}

function renderPrint(prescriptions, appointments = [], anamneses = [], scales = []) {
    vi.spyOn(apiService, "get").mockImplementation((path) => {
        const answers = {
            "/patients/5": PATIENT,
            "/patients/5/appointments": appointments,
            "/patients/5/prescriptions": prescriptions,
            "/patients/5/anamneses": anamneses,
            "/patients/5/scales/responses": scales,
        };
        return Promise.resolve(answers[path]);
    });
    const router = createMemoryRouter([{path: "/paciente/:patientId/impressao", element: <Impressao/>}],
        {initialEntries: ["/paciente/5/impressao"]});
    render(<RouterProvider router={router}/>);
}

describe("Impressao", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    // a receita em uso eh estado, n historico: o medico q recebe a impressao de 90 dias precisa ver
    // a receita vigente emitida ha 200 dias, senao conclui q o paciente n toma nada
    it("a prescricao em uso aparece msm emitida antes do periodo", async () => {
        renderPrint([prescriptionOf(1, 200, true, "Óleo de CBD 20%")]);

        const section = (await screen.findByRole("heading", {name: "Prescrição em uso"})).closest("section");
        expect(within(section).getByText("Óleo de CBD 20%")).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "Prescrições (0)"})).toBeInTheDocument();
        expect(screen.getByText("Nenhuma prescrição no período.")).toBeInTheDocument();
    });

    // dentro do periodo ela fica na secao e na lista, n some da lista
    it("a prescricao em uso dentro do periodo fica na secao e na lista", async () => {
        renderPrint([prescriptionOf(1, 10, true, "Óleo de CBD 20%"), prescriptionOf(2, 200, false, "Óleo antigo")]);

        await screen.findByRole("heading", {name: "Prescrição em uso"});
        expect(screen.getAllByText("Óleo de CBD 20%")).toHaveLength(2);
        expect(screen.getByRole("heading", {name: "Prescrições (1)"})).toBeInTheDocument();
        expect(screen.queryByText("Óleo antigo")).not.toBeInTheDocument();
    });

    // o plantonista q recebe a impressao do paciente arquivado lia "Prescrição em uso" (revisao das regras R2)
    it("do paciente arquivado a secao diz ultima prescricao e quando o acompanhamento acabou", async () => {
        renderPrint([{...prescriptionOf(1, 200, true, "Óleo de CBD 20%"), followUpEndedAt: "2026-11-10"}]);

        const heading = await screen.findByRole("heading",
            {name: "Última prescrição, acompanhamento encerrado em 10/11/2026"});
        expect(within(heading.closest("section")).getByText("Óleo de CBD 20%")).toBeInTheDocument();
        expect(screen.queryByRole("heading", {name: "Prescrição em uso"})).not.toBeInTheDocument();
    });

    it("sem prescricao em uso a secao diz isso", async () => {
        renderPrint([prescriptionOf(2, 200, false, "Óleo antigo")]);

        await screen.findByRole("heading", {name: "Prescrição em uso"});
        expect(screen.getByText("Nenhuma prescrição em uso.")).toBeInTheDocument();
    });

    // a ficha de maio lista varfarina e alergia, e o papel de novembro dizia so "Nenhuma anamnese no periodo"
    // a mais recente sai sempre, como a receita em uso, com a data de preenchimento a vista (revisao das regras R10)
    it("a anamnese mais recente aparece msm preenchida antes do periodo", async () => {
        const oldAnamnesis = {
            id: 4, assessmentDate: toIsoDate(addDays(new Date(), -200)), annulled: false,
            currentMedication: "Varfarina 5 mg",
        };
        renderPrint([], [], [oldAnamnesis]);

        const section = (await screen.findByRole("heading", {name: "Anamnese mais recente"})).closest("section");
        expect(within(section).getByText("Varfarina 5 mg")).toBeInTheDocument();
        expect(within(section).getByRole("heading", {name: /^Preenchida em /})).toBeInTheDocument();
        expect(screen.getByRole("heading", {name: "Anamnese (0)"})).toBeInTheDocument();
        expect(screen.getByText("Nenhuma anamnese no período.")).toBeInTheDocument();
    });

    // a anulada n vale como relato, entao a secao fica com a anterior valida
    it("a anamnese anulada n conta como a mais recente", async () => {
        const annulled = {id: 5, assessmentDate: toIsoDate(addDays(new Date(), -5)), annulled: true, pain: "Ficha errada"};
        const valid = {id: 4, assessmentDate: toIsoDate(addDays(new Date(), -200)), annulled: false, pain: "Dor no joelho"};
        renderPrint([], [], [annulled, valid]);

        const section = (await screen.findByRole("heading", {name: "Anamnese mais recente"})).closest("section");
        expect(within(section).getByText("Dor no joelho")).toBeInTheDocument();
        expect(within(section).queryByText("Ficha errada")).not.toBeInTheDocument();
    });

    it("sem anamnese a secao diz isso", async () => {
        renderPrint([]);

        await screen.findByRole("heading", {name: "Anamnese mais recente"});
        expect(screen.getByText("Nenhuma anamnese preenchida.")).toBeInTheDocument();
    });

    // o chrome quebrava a folha dentro da borda e do espaco de cima do cartao, antes do topo, e o titulo
    // ficava sozinho no pe da folha com a moldura vazia embaixo. o jsdom n monta folha, entao o teste
    // confere o q a regra de impressao precisa: todo cartao comeca pelo topo e no papel n tem borda
    // nem espaco antes dele, e os campos saem em bloco, pq a grade empurrava a linha inteira de folha
    it("no papel o cartao n tem nada antes do topo e os campos correm de uma folha pra outra", async () => {
        renderPrint([prescriptionOf(1, 10, true, "Óleo de CBD 20%")], [APPOINTMENT], [ANAMNESIS]);

        await screen.findByRole("heading", {name: "Prescrição em uso"});
        const cards = document.querySelectorAll("section section article");
        // a anamnese do periodo sai tbm na secao da mais recente, como a receita em uso
        expect(cards).toHaveLength(5);
        cards.forEach((card) => expect(card.firstElementChild.tagName).toBe("HEADER"));

        const css = readFileSync(import.meta.dirname + "/impressao.module.css", "utf8");
        const printRules = css.slice(css.indexOf("@media print"));
        expect(printRules).toMatch(/\.section article \{[^}]*border: 0;[^}]*padding: 0 /);
        expect(printRules).toMatch(/\.section article > header \{[^}]*padding-top: /);
        expect(printRules).toMatch(/\.section article dl \{[^}]*display: block;/);
    });

    // a impressao da prescritora mostra a correcao da escala com o resultado de antes, como o historico.
    // a api so manda a correcao pra prescritora, entao a do paciente fica como antes (revisao das regras R4)
    it("a escala corrigida sai com a data da correcao e o resultado de antes", async () => {
        const day = toIsoDate(addDays(new Date(), -10));
        const correctedAt = toIsoDate(addDays(new Date(), -2)) + "T10:00:00";
        renderPrint([], [], [], [
            {
                id: 9, scaleName: "Escala de ansiedade de Hamilton", periodStart: day, periodEnd: day,
                annulled: false, result: "12 pontos", correctedAt, previousResult: "30 pontos",
            },
            {
                id: 10, scaleName: "Acompanhamento semanal", periodStart: day, periodEnd: day,
                annulled: false, result: "Dor 3 de 10", correctedAt: null, previousResult: null,
            },
        ]);

        const corrected = (await screen.findByText("Escala de ansiedade de Hamilton")).closest("tr");
        expect(within(corrected).getByText("12 pontos", {exact: false})).toBeInTheDocument();
        expect(within(corrected).getByText("corrigida em " + formatDate(correctedAt) + " · antes: 30 pontos"))
            .toBeInTheDocument();
        const notCorrected = screen.getByText("Acompanhamento semanal").closest("tr");
        expect(within(notCorrected).queryByText(/corrigida em/)).not.toBeInTheDocument();
    });
});
