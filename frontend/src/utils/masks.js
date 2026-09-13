// mostra cpf e telefone formatados enquanto a pessoa digita
// a api recebe so os numeros

export function onlyDigits(text) {
    return (text || "").replace(/\D/g, "");
}

// 52998224725 vira 529.982.247-25
export function formatCpf(text) {
    const digits = onlyDigits(text).slice(0, 11);
    if (digits.length <= 3) {
        return digits;
    }
    if (digits.length <= 6) {
        return digits.slice(0, 3) + "." + digits.slice(3);
    }
    if (digits.length <= 9) {
        return digits.slice(0, 3) + "." + digits.slice(3, 6) + "." + digits.slice(6);
    }
    return digits.slice(0, 3) + "." + digits.slice(3, 6) + "." + digits.slice(6, 9) + "-" + digits.slice(9);
}

// 49999887766 vira (49) 99988-7766 e 4933221100 vira (49) 3322-1100
export function formatPhone(text) {
    const digits = onlyDigits(text).slice(0, 11);
    if (digits.length === 0) {
        return "";
    }
    if (digits.length <= 2) {
        return "(" + digits;
    }

    const areaCode = digits.slice(0, 2);
    const number = digits.slice(2);
    // celular tem 9 numeros depois do ddd e fixo tem 8
    const firstPartLength = number.length > 8 ? 5 : 4;
    if (number.length <= firstPartLength) {
        return "(" + areaCode + ") " + number;
    }
    return "(" + areaCode + ") " + number.slice(0, firstPartLength) + "-" + number.slice(firstPartLength);
}
