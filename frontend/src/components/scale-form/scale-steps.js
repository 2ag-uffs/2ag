// no celular a escala do paciente vai uma pergunta por tela
// o valor q o sistema calcula n eh pergunta, entao vai na tela da pergunta de antes dele
// (o tempo na cama do diario do sono fica junto do horario de levantar)
export function stepsOf(items) {
    const steps = [];
    let computedBefore = [];
    items.forEach((item) => {
        if (item.type !== "CALCULADO") {
            steps.push([...computedBefore, item]);
            computedBefore = [];
        } else if (steps.length > 0) {
            steps[steps.length - 1].push(item);
        } else {
            computedBefore.push(item);
        }
    });
    return steps;
}

// a tela onde fica o item, ou -1
export function stepOfKey(steps, key) {
    return steps.findIndex((stepItems) => stepItems.some((item) => item.key === key));
}
