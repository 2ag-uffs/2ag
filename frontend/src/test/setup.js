// deixa os testes usarem toBeInTheDocument e parecidos
// e limpa a tela montada entre um teste e outro
import "@testing-library/jest-dom/vitest";
import {cleanup, configure} from "@testing-library/react";
import {afterEach} from "vitest";

// com a suite toda rodando junto uma tela q salva, troca de endereco e carrega de novo passava de 1s
// e o findBy desistia antes, entao o teste falhava de vez em quando sem nada errado na tela
configure({asyncUtilTimeout: 3000});

afterEach(() => {
    cleanup();
});

// o jsdom n rola a tela e o ScrollRestoration do layout chama isso a cada tela
window.scrollTo = () => {};
// o cadastro tbm rola ate o aviso de erro la em cima
Element.prototype.scrollIntoView = () => {};
