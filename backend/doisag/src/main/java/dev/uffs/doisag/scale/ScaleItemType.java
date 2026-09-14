package dev.uffs.doisag.scale;

// o jeito q cada item eh preenchido e guardado
// a tela generica olha pra isso pra saber q campo desenhar
public enum ScaleItemType {

    // nota numa regua de min a max com as ancoras das pontas
    NOTA,
    // lista de opcoes com texto proprio, tipo a frequencia de 0 a 3
    ESCOLHA,
    // horario do dia
    HORA,
    // quantidade de minutos
    MINUTOS,
    // quantidade de horas q aceita meia hora
    HORAS,
    // contagem simples
    NUMERO,
    SIM_NAO,
    TEXTO,
    // valor q o sistema calcula e a pessoa n preenche
    CALCULADO
}
