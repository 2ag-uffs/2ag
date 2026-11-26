import {useEffect} from "react";
import {PHONE_QUERY} from "./phone-width.js";

// a partir de 20 px de letra (125%) o cabecalho, o contador, a barra de acoes e o menu presos tomavam a tela do celular
const LARGE_TEXT_SIZE = 20;
// o msm limite da janela baixa do css, @media (max-height: 30rem), medido no q aparece acima do teclado
const SHORT_WINDOW_REMS = 30;
// campos q n abrem o teclado da tela
const NO_KEYBOARD_TYPES = ["checkbox", "radio", "button", "submit", "range", "file"];

function rootSizeOf() {
    return parseFloat(getComputedStyle(document.documentElement).fontSize) || 16;
}

// a letra grande so solta as barras no celular: no computador e no tablet a janela tem altura de sobra pra elas
// navegador sem matchMedia, como o jsdom dos testes, fica com a tela de computador
function isPhoneWidth() {
    return typeof window.matchMedia === "function" && window.matchMedia(PHONE_QUERY).matches;
}

function opensKeyboard(element) {
    if (!element) {
        return false;
    }
    if (element.tagName === "TEXTAREA") {
        return true;
    }
    return element.tagName === "INPUT" && !NO_KEYBOARD_TYPES.includes(element.type);
}

// no chrome do android e no safari o teclado so encolhe a parte visivel da tela (visualViewport),
// entao o @media da janela baixa n dispara e o menu e a barra de acoes ficavam atras do teclado
function isKeyboardOpen(rootSize) {
    const visible = window.visualViewport;
    return Boolean(visible) && opensKeyboard(document.activeElement) && visible.height < SHORT_WINDOW_REMS * rootSize;
}

function updateLooseBars() {
    const rootSize = rootSizeOf();
    const largeTextOnPhone = rootSize >= LARGE_TEXT_SIZE && isPhoneWidth();
    document.documentElement.toggleAttribute("data-loose-bars", largeTextOnPhone || isKeyboardOpen(rootSize));
}

// regua escondida de 1rem: ela muda de largura junto com a letra do html
function addRemRuler() {
    const ruler = document.createElement("div");
    ruler.setAttribute("aria-hidden", "true");
    ruler.style.position = "absolute";
    ruler.style.top = "0";
    ruler.style.left = "0";
    ruler.style.width = "1rem";
    ruler.style.height = "0";
    ruler.style.visibility = "hidden";
    ruler.style.pointerEvents = "none";
    document.body.appendChild(ruler);
    return ruler;
}

// o @media (max-height: 30rem) mede o rem com a letra padrao do navegador, e n com a do html, e so olha a janela,
// entao a letra grande e o teclado aberto n soltavam as barras como a janela baixa. aqui o html ganha o
// data-loose-bars e o css solta as msm barras
export function useLooseBars() {
    useEffect(() => {
        updateLooseBars();
        const visible = window.visualViewport;
        window.addEventListener("resize", updateLooseBars);
        // o teclado abre e fecha com o foco nos campos
        document.addEventListener("focusin", updateLooseBars);
        document.addEventListener("focusout", updateLooseBars);
        if (visible) {
            visible.addEventListener("resize", updateLooseBars);
        }
        // a letra q a pessoa aumenta com a tela aberta n dispara resize, e as barras so se soltavam no foco do
        // primeiro toque, q mudava o menu de lugar no meio dele e o toque se perdia. a regua avisa antes
        // o jsdom dos testes n tem ResizeObserver
        let ruler = null;
        let rulerObserver = null;
        if (typeof ResizeObserver === "function") {
            ruler = addRemRuler();
            rulerObserver = new ResizeObserver(updateLooseBars);
            rulerObserver.observe(ruler);
        }
        return () => {
            window.removeEventListener("resize", updateLooseBars);
            document.removeEventListener("focusin", updateLooseBars);
            document.removeEventListener("focusout", updateLooseBars);
            if (visible) {
                visible.removeEventListener("resize", updateLooseBars);
            }
            if (rulerObserver) {
                rulerObserver.disconnect();
                ruler.remove();
            }
            document.documentElement.removeAttribute("data-loose-bars");
        };
    }, []);
}
