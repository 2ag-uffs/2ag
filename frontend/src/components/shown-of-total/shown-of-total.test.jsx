import {render, screen} from "@testing-library/react";
import {describe, expect, it} from "vitest";
import ShownOfTotal from "./shown-of-total.jsx";

describe("ShownOfTotal", () => {
    it("avisa qnd a lista do cartao eh so uma parte do total", () => {
        render(<ShownOfTotal shown={5} total={12}/>);

        expect(screen.getByText("Mostrando 5 de 12.")).toBeInTheDocument();
    });

    it("n aparece qnd a lista cabe inteira no cartao", () => {
        render(<ShownOfTotal shown={3} total={3}/>);

        expect(screen.queryByText(/Mostrando/)).not.toBeInTheDocument();
    });
});
