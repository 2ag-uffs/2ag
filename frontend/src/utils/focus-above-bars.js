import {useEffect} from "react";

// folga entre o campo com foco e a barra, em px
const GAP = 12;

// as barras presas no pe da tela tem o data-bottom-bar: a de acoes dos formularios e o menu de baixo do celular
// so contam as q cobrem o campo pelo lado e estao coladas no pe da janela, ou na barra q ja esta colada nele,
// como a de acoes q para em cima do menu de baixo. a de acoes fora do lugar preso fica no fim do formulario,
// embaixo de todos os campos, e a lateral fica do lado do formulario
function coveredTopOf(fieldBox) {
    const barBoxes = Array.from(document.querySelectorAll("[data-bottom-bar]"))
        .map((bar) => bar.getBoundingClientRect())
        .filter((barBox) => barBox.height > 0 && barBox.left < fieldBox.right && barBox.right > fieldBox.left);
    let coveredTop = window.innerHeight;
    let foundBar = true;
    while (foundBar) {
        foundBar = false;
        for (const barBox of barBoxes) {
            if (barBox.top < coveredTop && barBox.bottom >= coveredTop - 2) {
                coveredTop = barBox.top;
                foundBar = true;
            }
        }
    }
    return coveredTop;
}

// sobe a pagina so o bastante pro elemento com foco sair de baixo das barras
function showAboveBars(element) {
    // o botao da propria barra n tem como sair de baixo dela, e o modal fica parado por cima da pagina
    if (document.activeElement !== element || element.closest("[data-bottom-bar], [role='dialog']")) {
        return;
    }
    const fieldBox = element.getBoundingClientRect();
    if (fieldBox.height === 0) {
        return;
    }
    const hiddenPart = fieldBox.bottom + GAP - coveredTopOf(fieldBox);
    // no celular o cabecalho fica preso em cima, e o index.css ja desconta ele no scroll-padding-top
    const headerBottom = parseFloat(getComputedStyle(document.documentElement).scrollPaddingTop) || 0;
    // campo mais alto q o espaco livre sobe so ate o topo dele, onde ficam o rotulo e o cursor
    const distance = Math.min(hiddenPart, fieldBox.top - headerBottom - GAP);
    if (distance > 0) {
        window.scrollBy(0, distance);
    }
}

// o scroll-padding do index.css so vale qnd o navegador precisa rolar ate o campo, e o campo q ja esta
// dentro da janela mas atras da barra de acoes ou do menu de baixo ficava escondido (CT-39)
// o navegador rola dps do evento de foco, entao a conferencia espera o proximo quadro
export function useFocusAboveBars() {
    useEffect(() => {
        const handleFocus = (event) => {
            const element = event.target;
            requestAnimationFrame(() => showAboveBars(element));
        };
        document.addEventListener("focusin", handleFocus);
        return () => document.removeEventListener("focusin", handleFocus);
    }, []);
}
