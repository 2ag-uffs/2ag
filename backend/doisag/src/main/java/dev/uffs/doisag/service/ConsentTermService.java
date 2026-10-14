package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ConsentTermDTO;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.model.ConsentAcceptance;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.ConsentAcceptanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

// termo de consentimento q o paciente aceita no cadastro (RF36)
// o texto fica num arquivo e a versao muda sempre q o texto mudar
@Service
public class ConsentTermService {

    private static final Logger log = LoggerFactory.getLogger(ConsentTermService.class);

    // troque a versao junto com o texto do arquivo
    public static final String CURRENT_VERSION = "2026-09-rascunho";

    private static final String TERM_FILE = "consent/termo-de-consentimento.txt";
    private static final String CLINIC_NAME_MARKER = "[NOME DA CLÍNICA]";
    private static final String DPO_CONTACT_MARKER = "[CONTATO DO ENCARREGADO DE DADOS DA CLÍNICA]";

    private final ConsentAcceptanceRepository consentAcceptanceRepository;
    private final String termText;

    public ConsentTermService(ConsentAcceptanceRepository consentAcceptanceRepository,
                              @Value("${api.clinic.name:}") String clinicName,
                              @Value("${api.clinic.dpo-contact:}") String dpoContact) {
        this.consentAcceptanceRepository = consentAcceptanceRepository;
        String text = readTermFile();
        text = fillMarker(text, CLINIC_NAME_MARKER, clinicName, "CLINIC_NAME");
        text = fillMarker(text, DPO_CONTACT_MARKER, dpoContact, "CLINIC_DPO_CONTACT");
        this.termText = text;
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

    // paciente q aceitou uma versao antiga precisa aceitar a atual
    // conta sem aceite nenhum so existe em teste e seed, o cadastro sempre grava um
    public boolean needsNewAcceptance(Users user) {
        if (!(user instanceof Patient)) {
            return false;
        }
        return consentAcceptanceRepository.existsByUserId(user.getId())
                && !consentAcceptanceRepository.existsByUserIdAndTermVersion(user.getId(), CURRENT_VERSION);
    }

    @Transactional
    public void acceptNewVersion(Patient patient, String termVersion) {
        if (!isCurrentVersion(termVersion)) {
            throw new BusinessException(PatientService.OUTDATED_TERM_MESSAGE);
        }
        // aceitar de novo a msm versao n duplica o registro
        if (!consentAcceptanceRepository.existsByUserIdAndTermVersion(patient.getId(), termVersion)) {
            registerAcceptance(patient, termVersion);
        }
    }

    // sem a variavel o marcador fica no texto e a api so avisa, pra teste e ambiente local subirem
    private String fillMarker(String text, String marker, String value, String variableName) {
        if (value == null || value.isBlank()) {
            log.warn("{} vazio: o termo de consentimento sai com {}", variableName, marker);
            return text;
        }
        return text.replace(marker, value.trim());
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
