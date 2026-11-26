import {useEffect} from "react";
import {PHONE_QUERY} from "./phone-width.js";

// a partir de 20 px de letra (125%) o cabecalho, o contador, a barra de acoes e o menu presos tomavam a tela do celular
const LARGE_TEXT_SIZE = 20;

function rootSizeOf() {
    return parseFloat(getComputedStyle(document.documentElement).fontSize) || 16;
}

// a letra grande so solta as barras no celular: no computador e no tablet a janela tem altura de sobra pra elas
// navegador sem matchMedia, como o jsdom dos testes, fica com a tela de computador
function isPhoneWidth() {
    return typeof window.matchMedia === "function" && window.matchMedia(PHONE_QUERY).matches;
}

function updateLooseBars() {
    document.documentElement.toggleAttribute("data-loose-bars", rootSizeOf() >= LARGE_TEXT_SIZE && isPhoneWidth());
}

// o @media (max-height: 30rem) mede o rem com a letra padrao do navegador, e n com a do html, entao a letra
// grande n soltava as barras como a janela baixa. aqui o html ganha o data-loose-bars e o css solta as msm barras
export function useLooseBars() {
    useEffect(() => {
        updateLooseBars();
        const visible = window.visualViewport;
        window.addEventListener("resize", updateLooseBars);
        if (visible) {
            visible.addEventListener("resize", updateLooseBars);
        }
        return () => {
            window.removeEventListener("resize", updateLooseBars);
            if (visible) {
                visible.removeEventListener("resize", updateLooseBars);
            }
            document.documentElement.removeAttribute("data-loose-bars");
        };
    }, []);
}
