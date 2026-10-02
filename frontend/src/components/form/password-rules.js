// as mesmas regras de senha da api
// a tela marca cada uma conforme a pessoa digita

// a api guarda a senha com bcrypt, q so aceita 72 bytes, e letra com acento ou emoji ocupa mais de um
export function fitsInBcrypt(password) {
    return new TextEncoder().encode(password).length <= 72;
}

export const PASSWORD_RULES = [
    {
        id: "length",
        label: "De 8 a 64 caracteres (acento e emoji contam mais)",
        isMet: (password) => password.length >= 8 && password.length <= 64 && fitsInBcrypt(password),
    },
    {
        id: "uppercase",
        label: "Uma letra maiúscula",
        isMet: (password) => /\p{Lu}/u.test(password),
    },
    {
        id: "number",
        label: "Um número",
        isMet: (password) => /\d/.test(password),
    },
    {
        id: "symbol",
        label: "Um símbolo, como ! @ # ou -",
        isMet: (password) => /[^\p{L}\p{N}]/u.test(password),
    },
];

export function isStrongPassword(password) {
    return PASSWORD_RULES.every((rule) => rule.isMet(password));
}
