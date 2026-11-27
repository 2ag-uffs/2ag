import {describe, expect, it} from "vitest";
import {declarationsOf, mediaRules, readCss} from "../test/css-rules.js";

// decisao da dona do projeto, novembro de 2026: o q a pessoa precisa ler pra agir n fica menor q o corpo do texto
// a mensagem de erro do campo, a dica embaixo do campo, o nome do campo nos cartoes e, no celular,
// o texto do botao pequeno saiam em 14 px. agora tem o tamanho do corpo (16 px) pro paciente,
// pro prescritor e pra administracao, como uma regra so da escala de letras

const BODY = "var(--text-md)";
const MOBILE = "@media (max-width: 47.99rem)";

const FIELD_ERRORS = [
    ["../components/form/form.module.css", ".error"],
    ["../components/scale-form/scale-form.module.css", ".error"],
    ["../pages/sign-up/sign-up.module.css", ".consentError"],
    ["../pages/prescription-form/prescription-form.module.css", ".fieldError"],
    ["../pages/agenda-disponibilidade/agenda-disponibilidade.module.css", ".periodError"],
    ["../pages/admin/admin-prescribers.module.css", ".fieldError"],
];

const FIELD_HINTS = [
    ["../components/form/form.module.css", ".hint"],
    ["../components/form/form.module.css", ".checklistItem"],
    ["../components/form/form.module.css", ".checklistItemMet"],
    ["../components/invite-patient-modal/invite-patient-modal.module.css", ".hint"],
    ["../components/export-modal/export-modal.module.css", ".help"],
    ["../pages/sign-up/sign-up.module.css", ".sectionHint"],
    ["../pages/profile/profile.module.css", ".hint"],
    ["../pages/prescription-form/prescription-form.module.css", ".hint"],
    ["../pages/progresso/progresso.module.css", ".checkHint"],
];

const CARD_LABELS = [
    ["../components/prescription-card/prescription-card.module.css", ".details dt"],
    ["../components/prescription-card/prescription-card.module.css", ".block h4"],
    ["../components/consultation-card/consultation-card.module.css", ".details dt"],
    ["../components/anamnesis-view/anamnesis-view.module.css", ".details dt"],
    ["../pages/impressao/impressao.module.css", ".headData dt"],
    ["../pages/profile/profile.module.css", ".readOnlyInfo dt"],
];

describe("o q a pessoa precisa ler tem o tamanho do corpo do texto", () => {
    it.each(FIELD_ERRORS)("mensagem de erro do campo: %s %s", (file, selector) => {
        expect(declarationsOf(readCss(import.meta.url, file), selector)["font-size"]).toBe(BODY);
    });

    it.each(FIELD_HINTS)("dica embaixo do campo: %s %s", (file, selector) => {
        expect(declarationsOf(readCss(import.meta.url, file), selector)["font-size"]).toBe(BODY);
    });

    it.each(CARD_LABELS)("nome do campo no cartao: %s %s", (file, selector) => {
        expect(declarationsOf(readCss(import.meta.url, file), selector)["font-size"]).toBe(BODY);
    });

    it("o botao pequeno tem a letra do corpo no celular e continua com a pequena no computador", () => {
        const buttonCss = readCss(import.meta.url, "./button.css");

        expect(declarationsOf(mediaRules(buttonCss, MOBILE), ":where(.button-small)")["font-size"]).toBe(BODY);
        expect(declarationsOf(buttonCss, ":where(.button-small)")["font-size"]).toBe("var(--text-sm)");
    });

    it("o corpo do texto tem 16 px", () => {
        const tokens = declarationsOf(readCss(import.meta.url, "./tokens.css"), ":root");

        expect(parseFloat(tokens["--text-md"]) * 16).toBe(16);
    });
});
