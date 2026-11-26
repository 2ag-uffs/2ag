package dev.uffs.doisag.infra;

import dev.uffs.doisag.security.SecureTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// lembra por alguns minutos o registro q cada envio criou (decisao de novembro/2026)
//
// a resposta do salvar pode se perder no caminho com o registro ja gravado, e a tela manda tudo de novo
// a tela sorteia uma chave por formulario aberto e manda no cabecalho X-Request-Key: o envio q chega
// com a mesma chave e o mesmo conteudo recebe o registro q ja existe, em vez de criar outro igual
// a receita emitida de novo de proposito sai de um formulario novo, com outra chave, e eh criada como sempre
//
// fica em memoria, como a contagem do login: a api roda numa instancia so e reiniciar so esquece
// do conteudo e da chave fica so o hash, entao texto clinico n mora aqui
@Component
public class RepeatedSendMemory {

    public static final String HEADER = "X-Request-Key";

    private static final Duration KEEP_TIME = Duration.ofMinutes(10);
    private static final int MAX_KEY_LENGTH = 100;

    private final Clock clock;
    private final Map<String, SavedSend> sends = new HashMap<>();

    @Autowired
    public RepeatedSendMemory() {
        this(Clock.systemDefaultZone());
    }

    // o teste passa um relogio proprio pra conferir o fim do prazo sem esperar
    RepeatedSendMemory(Clock clock) {
        this.clock = clock;
    }

    // id do registro q esse mesmo envio ja criou, ou null qnd eh um envio novo
    // o lugar diz de quem eh o registro (a consulta da receita, o prescritor e o paciente da consulta)
    public synchronized Long findSaved(String place, String requestKey, Object content) {
        if (!isUsable(requestKey)) {
            return null;
        }
        SavedSend saved = sends.get(keyOf(place, requestKey));
        if (saved == null || saved.hasFinished(clock.instant()) || !saved.contentHash.equals(hashOf(content))) {
            return null;
        }
        return saved.recordId;
    }

    public synchronized void remember(String place, String requestKey, Object content, Long recordId) {
        if (!isUsable(requestKey)) {
            return;
        }
        Instant now = clock.instant();
        sends.values().removeIf(saved -> saved.hasFinished(now));
        sends.put(keyOf(place, requestKey), new SavedSend(recordId, hashOf(content), now));
    }

    // sem chave, ou com uma grande demais, o envio eh tratado como novo, como antes
    private boolean isUsable(String requestKey) {
        return requestKey != null && !requestKey.isBlank() && requestKey.length() <= MAX_KEY_LENGTH;
    }

    private String keyOf(String place, String requestKey) {
        return place + "|" + SecureTokens.hashToken(requestKey);
    }

    // os dto sao records, entao o texto deles tem todos os campos e muda qnd qqr um muda
    private String hashOf(Object content) {
        return SecureTokens.hashToken(String.valueOf(content));
    }

    private static class SavedSend {
        private final Long recordId;
        private final String contentHash;
        private final Instant savedAt;

        private SavedSend(Long recordId, String contentHash, Instant savedAt) {
            this.recordId = recordId;
            this.contentHash = contentHash;
            this.savedAt = savedAt;
        }

        private boolean hasFinished(Instant now) {
            return !savedAt.plus(KEEP_TIME).isAfter(now);
        }
    }
}
