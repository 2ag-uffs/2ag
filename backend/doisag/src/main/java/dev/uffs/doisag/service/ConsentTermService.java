package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ConsentTermDTO;
import dev.uffs.doisag.dto.ResearchConsentDTO;
import dev.uffs.doisag.enums.ConsentType;
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
import java.util.Optional;

// termo de consentimento q o paciente aceita no cadastro (RF36)
// e o de pesquisa, separado, opcional e q pode ser retirado (RF37)
// cada texto fica num arquivo e a versao muda sempre q o texto mudar
@Service
public class ConsentTermService {

    private static final Logger log = LoggerFactory.getLogger(ConsentTermService.class);

    // troque a versao junto com o texto do arquivo
    public static final String CURRENT_VERSION = "2026-09-rascunho";
    public static final String RESEARCH_VERSION = "2026-10-rascunho";

    public static final String OUTDATED_RESEARCH_MESSAGE =
            "O termo de pesquisa foi atualizado. Leia a versão nova antes de aceitar";

    private static final String TERM_FILE = "consent/termo-de-consentimento.txt";
    private static final String RESEARCH_FILE = "consent/termo-de-pesquisa.txt";
    private static final String CLINIC_NAME_MARKER = "[NOME DA CLÍNICA]";
    private static final String DPO_CONTACT_MARKER = "[CONTATO DO ENCARREGADO DE DADOS DA CLÍNICA]";

    private final ConsentAcceptanceRepository consentAcceptanceRepository;
    private final String termText;
    private final String researchText;

    public ConsentTermService(ConsentAcceptanceRepository consentAcceptanceRepository,
                              @Value("${api.clinic.name:}") String clinicName,
                              @Value("${api.clinic.dpo-contact:}") String dpoContact) {
        this.consentAcceptanceRepository = consentAcceptanceRepository;
        if (clinicName == null || clinicName.isBlank()) {
            log.warn("CLINIC_NAME vazio: os termos saem com {}", CLINIC_NAME_MARKER);
        }
        if (dpoContact == null || dpoContact.isBlank()) {
            log.warn("CLINIC_DPO_CONTACT vazio: os termos saem com {}", DPO_CONTACT_MARKER);
        }
        this.termText = fillClinicData(readFile(TERM_FILE), clinicName, dpoContact);
        this.researchText = fillClinicData(readFile(RESEARCH_FILE), clinicName, dpoContact);
    }

    public ConsentTermDTO getCurrentTerm() {
        return new ConsentTermDTO(CURRENT_VERSION, termText);
    }

    public ConsentTermDTO getResearchTerm() {
        return new ConsentTermDTO(RESEARCH_VERSION, researchText);
    }

    // o cadastro so vale com a versao do termo q esta valendo agora
    public boolean isCurrentVersion(String termVersion) {
        return CURRENT_VERSION.equals(termVersion);
    }

    // guarda quem aceitou qual versao e quando
    public void registerAcceptance(Users user, String termVersion) {
        registerAcceptance(user, ConsentType.REGISTRATION, termVersion);
    }

    public void registerAcceptance(Users user, ConsentType termType, String termVersion) {
        ConsentAcceptance acceptance = new ConsentAcceptance();
        acceptance.setUser(user);
        acceptance.setTermType(termType);
        acceptance.setTermVersion(termVersion);
        acceptance.setAcceptedAt(LocalDateTime.now());
        consentAcceptanceRepository.save(acceptance);
    }

    // paciente q aceitou uma versao antiga do termo do cadastro precisa aceitar a atual
    // conta sem aceite nenhum so existe em teste e seed, o cadastro sempre grava um
    public boolean needsNewAcceptance(Users user) {
        if (!(user instanceof Patient)) {
            return false;
        }
        return consentAcceptanceRepository.existsByUserIdAndTermType(user.getId(), ConsentType.REGISTRATION)
                && !consentAcceptanceRepository.existsByUserIdAndTermTypeAndTermVersion(
                        user.getId(), ConsentType.REGISTRATION, CURRENT_VERSION);
    }

    @Transactional
    public void acceptNewVersion(Patient patient, String termVersion) {
        if (!isCurrentVersion(termVersion)) {
            throw new BusinessException(PatientService.OUTDATED_TERM_MESSAGE);
        }
        // aceitar de novo a msm versao n duplica o registro
        if (!consentAcceptanceRepository.existsByUserIdAndTermTypeAndTermVersion(
                patient.getId(), ConsentType.REGISTRATION, termVersion)) {
            registerAcceptance(patient, termVersion);
        }
    }

    // so vale o aceite da versao atual do texto de pesquisa q n foi retirado
    public boolean hasResearchConsent(Long userId) {
        return consentAcceptanceRepository.existsByUserIdAndTermTypeAndTermVersionAndRevokedAtIsNull(
                userId, ConsentType.RESEARCH, RESEARCH_VERSION);
    }

    // msm regra do hasResearchConsent, senao o perfil e a exportacao discordam
    public ResearchConsentDTO researchConsentOf(Long userId) {
        Optional<ConsentAcceptance> current = consentAcceptanceRepository
                .findFirstByUserIdAndTermTypeAndTermVersionAndRevokedAtIsNullOrderByAcceptedAtDesc(
                        userId, ConsentType.RESEARCH, RESEARCH_VERSION);
        if (current.isPresent()) {
            return new ResearchConsentDTO(true, current.get().getAcceptedAt(), false);
        }
        boolean acceptedOlderText = !consentAcceptanceRepository
                .findAllByUserIdAndTermTypeAndRevokedAtIsNull(userId, ConsentType.RESEARCH).isEmpty();
        return new ResearchConsentDTO(false, null, acceptedOlderText);
    }

    @Transactional
    public void acceptResearch(Patient patient, String termVersion) {
        if (!RESEARCH_VERSION.equals(termVersion)) {
            throw new BusinessException(OUTDATED_RESEARCH_MESSAGE);
        }
        if (!hasResearchConsent(patient.getId())) {
            registerAcceptance(patient, ConsentType.RESEARCH, termVersion);
        }
    }

    // a retirada marca a data e a linha fica, pra saber o q valia em cada exportacao
    @Transactional
    public void revokeResearch(Patient patient) {
        LocalDateTime now = LocalDateTime.now();
        for (ConsentAcceptance acceptance : consentAcceptanceRepository
                .findAllByUserIdAndTermTypeAndRevokedAtIsNull(patient.getId(), ConsentType.RESEARCH)) {
            acceptance.setRevokedAt(now);
        }
    }

    // sem a variavel o marcador fica no texto, pra teste e ambiente local subirem
    private String fillClinicData(String text, String clinicName, String dpoContact) {
        if (clinicName != null && !clinicName.isBlank()) {
            text = text.replace(CLINIC_NAME_MARKER, clinicName.trim());
        }
        if (dpoContact != null && !dpoContact.isBlank()) {
            text = text.replace(DPO_CONTACT_MARKER, dpoContact.trim());
        }
        return text;
    }

    // sem o arquivo do termo a api nem sobe pq n da pra cadastrar ninguem sem ele
    private String readFile(String file) {
        try {
            return new ClassPathResource(file).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("arquivo do termo nao encontrado: " + file, exception);
        }
    }
}
