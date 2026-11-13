import {readFileSync} from "node:fs";
import {dirname, join} from "node:path";
import {fileURLToPath} from "node:url";

// o jsdom n desenha a tela, entao os testes de layout leem as regras do css direto do arquivo
// o caminho eh a partir do arquivo de teste: readCss(import.meta.url, "./tela.module.css")
export function readCss(testFileUrl, relativePath) {
    return readFileSync(join(dirname(fileURLToPath(testFileUrl)), relativePath), "utf8");
}

// separa as regras do primeiro nivel: cada uma vira {selectors, body}
// um @media vira uma regra com o proprio @media no lugar do seletor e as regras de dentro no body
export function cssRules(cssText) {
    const css = cssText.replace(/\/\*[\s\S]*?\*\//g, "");
    const rules = [];
    let start = 0;
    while (css.indexOf("{", start) !== -1) {
        const open = css.indexOf("{", start);
        let depth = 1;
        let close = open + 1;
        while (depth > 0 && close < css.length) {
            if (css[close] === "{") {
                depth++;
            } else if (css[close] === "}") {
                depth--;
            }
            close++;
        }
        const header = css.slice(start, open).trim();
        rules.push({selectors: header.split(",").map((selector) => selector.trim()), body: css.slice(open + 1, close - 1)});
        start = close;
    }
    return rules;
}

// as regras de dentro de um @media
export function mediaRules(cssText, media) {
    const mediaRule = cssRules(cssText).find((rule) => rule.selectors.join(", ") === media);
    return mediaRule ? mediaRule.body : "";
}

// as declaracoes de um seletor, juntando as regras dele na ordem do arquivo como o navegador faz
export function declarationsOf(cssText, selector) {
    const declarations = {};
    cssRules(cssText)
        .filter((rule) => rule.selectors.includes(selector))
        .forEach((rule) => {
            rule.body.split(";").forEach((declaration) => {
                const colon = declaration.indexOf(":");
                if (colon !== -1) {
                    declarations[declaration.slice(0, colon).trim()] = declaration.slice(colon + 1).trim().replace(/\s+/g, " ");
                }
            });
        });
    return declarations;
}
