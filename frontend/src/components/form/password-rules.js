// as mesmas regras de senha da api
// a tela marca cada uma conforme a pessoa digita
export const PASSWORD_RULES = [
    {
        id: "length",
        label: "De 8 a 64 caracteres",
        isMet: (password) => password.length >= 8 && password.length <= 64,
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
