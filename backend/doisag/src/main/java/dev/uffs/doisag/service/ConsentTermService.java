package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ConsentTermDTO;
import dev.uffs.doisag.model.ConsentAcceptance;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.ConsentAcceptanceRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

// termo de consentimento q o paciente aceita no cadastro (RF36)
// o texto fica num arquivo e a versao muda sempre q o texto mudar
@Service
public class ConsentTermService {

    // troque a versao junto com o texto do arquivo
    public static final String CURRENT_VERSION = "2026-09-rascunho";

    private static final String TERM_FILE = "consent/termo-de-consentimento.txt";

    private final ConsentAcceptanceRepository consentAcceptanceRepository;
    private final String termText;

    public ConsentTermService(ConsentAcceptanceRepository consentAcceptanceRepository) {
        this.consentAcceptanceRepository = consentAcceptanceRepository;
        this.termText = readTermFile();
    }

    public ConsentTermDTO getCurrentTerm() {
        return new ConsentTermDTO(CURRENT_VERSION, termText);
    }

    // o cadastro so vale com a versao do termo q esta valendo agora
    public boolean isCurrentVersion(String termVersion) {
        return CURRENT_VERSION.equals(termVersion);
    }

    // guarda quem aceitou qual versao e quando
    public void registerAcceptance(Users user, String termVersion) {
        ConsentAcceptance acceptance = new ConsentAcceptance();
        acceptance.setUser(user);
        acceptance.setTermVersion(termVersion);
        acceptance.setAcceptedAt(LocalDateTime.now());
        consentAcceptanceRepository.save(acceptance);
    }

    // sem o arquivo do termo a api nem sobe pq n da pra cadastrar ninguem sem ele
    private String readTermFile() {
        try {
            return new ClassPathResource(TERM_FILE).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("arquivo do termo de consentimento nao encontrado", exception);
        }
    }
}
