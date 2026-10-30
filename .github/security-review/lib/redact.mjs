// tira dado pessoal do texto antes de ir pra api externa (lgpd)
// troca linha por linha entao a qtd de linhas e a numeracao n mudam

// os limites fixos deixam a busca rapida ate em linha gigante
const EMAIL = /[A-Za-z0-9._%+-]{1,64}(?:@|%40)[A-Za-z0-9-]{1,63}(?:\.[A-Za-z0-9-]{1,63}){0,8}\.[A-Za-z]{2,24}/g;
// antes e dps do numero so digito impede a troca entao letra colada tipo CPF123... ou o L do long n escondem o cpf
// o _ eh o separador de numero do java e do js
const CNPJ = /(?<!\d)\d{2}[._]?\d{3}[._]?\d{3}[/_]?\d{4}[-_]?\d{2}(?!\d)/g;
const CPF_FORMATTED = /(?<!\d)\d{3}\.\d{3}\.\d{3}-\d{2}(?!\d)/g;
const ELEVEN_DIGITS = /(?<!\d)\d{11}(?!\d)/g;
// cpf com separador misturado so sai qnd os digitos fecham a conta do cpf
const CPF_LOOSE = /(?<!\d)\d{3}[-. _]?\d{3}[-. _]?\d{3}[-. /_]?\d{2}(?!\d)/g;
// ddd com ou sem parenteses e zero e com ou sem +55 e o 9 do celular pode vir separado
const PHONE = /(?<![A-Za-z0-9])(?:\+?55[ .-]?)?(?:\(0?\d{2}\)|0?\d{2})[ .-]?(?:9[ .-]?)?\d{4}[ .-]?\d{4}(?!\d)/g;
// celular sem ddd sai mas o fixo sem ddd fica de fora pq parece intervalo de ano ou de porta
const MOBILE_NO_DDD = /(?<![A-Za-z0-9])9\d{4}-\d{4}(?!\d)/g;

export const TAGS = {
    cpf: "[cpf removido]",
    cnpj: "[cnpj removido]",
    email: "[email removido]",
    telefone: "[telefone removido]",
};

export function emptyCounts() {
    return { cpf: 0, cnpj: 0, email: 0, telefone: 0, segredo: 0 };
}

export function addCounts(total, more) {
    for (const kind of Object.keys(total)) {
        total[kind] += more[kind] || 0;
    }
    return total;
}

export function isValidCpf(digits) {
    if (!/^\d{11}$/.test(digits) || /^(\d)\1{10}$/.test(digits)) {
        return false;
    }
    for (const size of [9, 10]) {
        let sum = 0;
        for (let index = 0; index < size; index++) {
            sum += Number(digits[index]) * (size + 1 - index);
        }
        const checkDigit = (sum * 10) % 11 % 10;
        if (checkDigit !== Number(digits[size])) {
            return false;
        }
    }
    return true;
}

function redactLine(line, counts) {
    const swap = (kind) => () => {
        counts[kind]++;
        return TAGS[kind];
    };
    let text = line;
    text = text.replace(EMAIL, swap("email"));
    text = text.replace(CNPJ, swap("cnpj"));
    text = text.replace(CPF_FORMATTED, swap("cpf"));
    // 11 digitos pode ser cpf ou celular com ddd e os dois saem
    text = text.replace(ELEVEN_DIGITS, (digits) => {
        if (!isValidCpf(digits) && digits[2] === "9") {
            counts.telefone++;
            return TAGS.telefone;
        }
        counts.cpf++;
        return TAGS.cpf;
    });
    text = text.replace(CPF_LOOSE, (match) => {
        if (!isValidCpf(match.replace(/\D/g, ""))) {
            return match;
        }
        counts.cpf++;
        return TAGS.cpf;
    });
    text = text.replace(PHONE, swap("telefone"));
    text = text.replace(MOBILE_NO_DDD, swap("telefone"));
    return text;
}

export function redactText(text) {
    const counts = emptyCounts();
    const lines = String(text).split("\n").map((line) => redactLine(line, counts));
    return { text: lines.join("\n"), counts };
}
