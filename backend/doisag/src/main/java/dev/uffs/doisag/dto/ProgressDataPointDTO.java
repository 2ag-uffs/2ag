package dev.uffs.doisag.dto;

import java.time.LocalDate;

// dto simples pra mandar cada pontinho do gráfico
// uma data no eixo x e um valor no eixo y
//
// no escore de escala validada o ponto leva a faixa gravada junto, pq o
// numero sozinho n quer dizer nada (RN14). o corte vem qnd ele depende da
// resposta, como o MEEM q muda com a escolaridade do exame. fora isso os dois vem nulos
public record ProgressDataPointDTO(LocalDate date, Integer value, String band, Double cutoff) {

}
