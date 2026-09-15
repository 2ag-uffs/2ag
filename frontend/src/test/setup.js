// deixa os testes usarem toBeInTheDocument e parecidos
// e limpa a tela montada entre um teste e outro
import "@testing-library/jest-dom/vitest";
import {cleanup} from "@testing-library/react";
import {afterEach} from "vitest";

afterEach(() => {
    cleanup();
});

// o jsdom n rola a tela e o ScrollRestoration do layout chama isso a cada tela
window.scrollTo = () => {};
