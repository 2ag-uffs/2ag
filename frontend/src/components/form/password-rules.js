// as mesmas regras de senha da api
// a tela marca cada uma conforme a pessoa digita

const MIN_LENGTH = 8;
const MAX_LENGTH = 64;
// a api guarda a senha com bcrypt, q so aceita 72 bytes, e letra com acento ou emoji ocupa mais de um
const BCRYPT_MAX_BYTES = 72;

export function fitsInBcrypt(password) {
    return new TextEncoder().encode(password).length <= BCRYPT_MAX_BYTES;
}

// conta os caracteres como a api: o emoji eh um so, mesmo q o length do js conte 2
function characterCount(password) {
    return [...password].length;
}

function hasAllowedLength(password) {
    const count = characterCount(password);
    return count >= MIN_LENGTH && count <= MAX_LENGTH && fitsInBcrypt(password);
}

export const PASSWORD_RULES = [
    {
        id: "length",
        label: "De 8 a 64 caracteres (acento e emoji contam mais)",
        isMet: hasAllowedLength,
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
