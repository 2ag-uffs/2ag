package dev.uffs.doisag.dto;

import dev.uffs.doisag.enums.ScaleType;

// dto simples pra receber qual escala o prescritor quer designar
public record AssignScaleDTO(ScaleType scaleType) {

}