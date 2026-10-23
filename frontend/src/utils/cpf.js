import {onlyDigits} from "./masks.js";

// confere os dois digitos verificadores do cpf, a mesma conta q a api faz
// a api confere de novo: aqui eh so pra avisar antes de enviar

// peso 10..2 sobre os 9 primeiros (ou 11..2 sobre os 10), resto da divisao por 11
function checkDigit(digits) {
    let sum = 0;
    for (let index = 0; index < digits.length; index++) {
        sum += Number(digits[index]) * (digits.length + 1 - index);
    }
    const rest = sum % 11;
    return rest < 2 ? 0 : 11 - rest;
}

export function isValidCpf(text) {
    const digits = onlyDigits(text);
    // 111.111.111-11 fecha a conta mas n eh cpf de ninguem
    if (digits.length !== 11 || digits === digits[0].repeat(11)) {
        return false;
    }
    const firstTen = digits.slice(0, 9) + checkDigit(digits.slice(0, 9));
    return digits === firstTen + checkDigit(firstTen);
}
