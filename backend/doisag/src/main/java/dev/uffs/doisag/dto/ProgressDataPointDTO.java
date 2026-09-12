package dev.uffs.doisag.dto;

import java.time.LocalDate;

// dto simples pra mandar cada pontinho do gráfico
// uma data no eixo x e um valor no eixo y
public record ProgressDataPointDTO(LocalDate date, Integer value) {

}