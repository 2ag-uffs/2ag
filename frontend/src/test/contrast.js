import {declarationsOf, mediaRules} from "./css-rules.js";

// conta de contraste da wcag pros testes de cor, q leem os tokens direto do colors.css

// cor do css (#rrggbb ou rgba) em [r, g, b, alfa]
export function rgbaOf(color) {
    if (color.startsWith("#")) {
        return [1, 3, 5].map((index) => parseInt(color.slice(index, index + 2), 16)).concat(1);
    }
    const parts = color.match(/[\d.]+/g).map(Number);
    return [parts[0], parts[1], parts[2], parts.length > 3 ? parts[3] : 1];
}

// a cor q aparece na tela: a transparente se mistura com o fundo
export function onTopOf(color, backdrop) {
    return [0, 1, 2].map((index) => color[index] * color[3] + backdrop[index] * (1 - color[3]));
}

// luminancia relativa e contraste da wcag
function luminanceOf(rgb) {
    const [red, green, blue] = rgb.map((channel) => {
        const value = channel / 255;
        return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

export function contrastOf(first, second) {
    const lighter = Math.max(luminanceOf(first), luminanceOf(second));
    const darker = Math.min(luminanceOf(first), luminanceOf(second));
    return (lighter + 0.05) / (darker + 0.05);
}

// os tokens de cor de cada tema. o escuro aparece duas vezes no colors.css: o do botao e o do aparelho
export function themesOf(colorsCss) {
    const lightTheme = declarationsOf(colorsCss, ":root");
    return {
        "claro": lightTheme,
        "escuro escolhido no botao": {...lightTheme, ...declarationsOf(colorsCss, ":root[data-theme=\"dark\"]")},
        "escuro do aparelho": {
            ...lightTheme,
            ...declarationsOf(mediaRules(colorsCss, "@media (prefers-color-scheme: dark)"), ":root:not([data-theme=\"light\"])"),
        },
    };
}
