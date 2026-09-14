package dev.uffs.doisag.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.Map;

// as respostas de uma escala vao pro banco como json num campo de texto
//
// eh o q deixa escala nova entrar sem coluna nova: o formulario esta
// descrito no codigo e a resposta eh so o par item e valor (RNF08)
@Converter
public class ScaleAnswersConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> answers) {
        try {
            return objectMapper.writeValueAsString(answers == null ? Map.of() : answers);
        } catch (Exception error) {
            throw new IllegalStateException("Não foi possível gravar as respostas da escala", error);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception error) {
            throw new IllegalStateException("Não foi possível ler as respostas da escala", error);
        }
    }
}
