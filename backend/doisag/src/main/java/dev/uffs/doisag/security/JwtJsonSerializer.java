package dev.uffs.doisag.security;

import io.jsonwebtoken.io.AbstractSerializer;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.util.Map;

// o json de dentro do token escrito com o jackson 3, q ja vem com o spring boot 4
// o jjwt-jackson fazia isso, mas trazia o jackson 2 inteiro so pra esse uso (issue 37)
// o jjwt acha essa classe pelo META-INF/services, entao o TokenService n precisa apontar pra ela
public class JwtJsonSerializer extends AbstractSerializer<Map<String, ?>> {

    // o fluxo eh do jjwt, entao o jackson n fecha ele no fim
    static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .build();

    @Override
    protected void doSerialize(Map<String, ?> claims, OutputStream out) {
        MAPPER.writeValue(out, claims);
    }
}
