// as iniciais do primeiro e do ultimo nome, pro circulo ao lado do nome nas listas
export function initialsOf(name) {
    const words = name.split(" ").filter((word) => word !== "");
    if (words.length === 0) {
        return "?";
    }
    const lastWord = words.length > 1 ? words[words.length - 1] : "";
    return (words[0].charAt(0) + lastWord.charAt(0)).toUpperCase();
}

// o painel chama a pessoa pelo primeiro nome e pula titulo tipo dr. ou dra.
export function firstNameOf(name) {
    return name.split(" ").find((word) => !word.endsWith(".")) || name;
}
