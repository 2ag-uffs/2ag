package dev.uffs.doisag.security;

import io.jsonwebtoken.io.AbstractDeserializer;
import tools.jackson.core.type.TypeReference;

import java.io.Reader;
import java.util.Map;

// o json de dentro do token lido com o jackson 3, par do JwtJsonSerializer
public class JwtJsonDeserializer extends AbstractDeserializer<Map<String, ?>> {

    @Override
    protected Map<String, ?> doDeserialize(Reader reader) {
        return JwtJsonSerializer.MAPPER.readValue(reader, new TypeReference<Map<String, ?>>() {});
    }
}
