import {describe, expect, it} from "vitest";
import {currentFirst} from "./prescription-labels.js";

describe("currentFirst", () => {
    it("a vigente vai pro comeco e as outras ficam na ordem q vieram", () => {
        const prescriptions = [
            {id: 3, current: false},
            {id: 2, current: true},
            {id: 1, current: false},
        ];

        expect(currentFirst(prescriptions).map((prescription) => prescription.id)).toEqual([2, 3, 1]);
    });
});
