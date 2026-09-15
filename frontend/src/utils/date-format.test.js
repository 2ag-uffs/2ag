import {describe, expect, it} from "vitest";
import {addDays, formatDate, mondayOf, toIsoDate} from "./date-format.js";

describe("datas", () => {
    it("data sem hora da api sai no formato brasileiro sem voltar um dia", () => {
        expect(formatDate("2026-09-14")).toBe("14/09/2026");
        expect(formatDate("")).toBe("");
    });

    it("toIsoDate usa o dia local e n o utc", () => {
        expect(toIsoDate(new Date(2026, 8, 5, 23, 30))).toBe("2026-09-05");
    });

    it("addDays passa de mes sem mexer na data original", () => {
        const original = new Date(2026, 8, 30);
        expect(toIsoDate(addDays(original, 2))).toBe("2026-10-02");
        expect(toIsoDate(original)).toBe("2026-09-30");
    });

    it("mondayOf de um domingo volta pra segunda anterior", () => {
        expect(toIsoDate(mondayOf(new Date(2026, 8, 13)))).toBe("2026-09-07");
        expect(toIsoDate(mondayOf(new Date(2026, 8, 14)))).toBe("2026-09-14");
    });
});
