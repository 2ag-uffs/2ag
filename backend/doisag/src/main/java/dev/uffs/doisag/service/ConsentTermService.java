package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.ConsentTermDTO;
import dev.uffs.doisag.dto.GuardianDTO;
import dev.uffs.doisag.dto.ResearchConsentDTO;
import dev.uffs.doisag.enums.ConsentType;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.ConsentAcceptance;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.Users;
import dev.uffs.doisag.repository.ConsentAcceptanceRepository;
import dev.uffs.doisag.repository.PatientRepository;
import dev.uffs.doisag.security.SecureTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// termo de consentimento q o paciente aceita no cadastro (RF36)
// e o de pesquisa, separado, opcional e q pode ser retirado (RF37)
// cada texto fica num arquivo e a versao muda sempre q o texto mudar
@Service
public class ConsentTermService {

    private static final Logger log = LoggerFactory.getLogger(ConsentTermService.class);

    // troque a versao junto com o texto do arquivo
    public static final String CURRENT_VERSION = "2026-10-rascunho-2";
    public static final String RESEARCH_VERSION = "2026-10-rascunho-2";

    public static final String OUTDATED_RESEARCH_MESSAGE =
            "O termo de pesquisa foi atualizado. Leia a versão nova antes de aceitar";

    public static final String GUARDIAN_REQUIRED_MESSAGE =
            "Paciente com menos de 18 anos: informe o nome e o parentesco do responsável legal "
                    + "antes de aceitar o termo";

    // a pesquisa com menor espera o comite de etica definir o assentimento dele
    public static final String MINOR_RESEARCH_MESSAGE =
            "A pesquisa ainda não está aberta para pacientes com menos de 18 anos";

    private static final String TERM_FILE = "consent/termo-de-consentimento.txt";
    private static final String RESEARCH_FILE = "consent/termo-de-pesquisa.txt";
    private static final String CLINIC_NAME_MARKER = "[NOME DA CLÍNICA]";
    private static final String CLINIC_CNPJ_MARKER = "[CNPJ DA CLÍNICA]";
    private static final String DPO_CONTACT_MARKER = "[CONTATO DO ENCARREGADO DE DADOS DA CLÍNICA]";

    private final ConsentAcceptanceRepository consentAcceptanceRepository;
    private final PatientRepository patientRepository;
    private final AuditService auditService;
    private final String termText;
    private final String researchText;

    public ConsentTermService(ConsentAcceptanceRepository consentAcceptanceRepository,
                              PatientRepository patientRepository,
                              AuditService auditService,
                              @Value("${api.clinic.name:}") String clinicName,
                              @Value("${api.clinic.cnpj:}") String clinicCnpj,
                              @Value("${api.clinic.dpo-contact:}") String dpoContact,
                              Environment environment) {
        this.consentAcceptanceRepository = consentAcceptanceRepository;
        this.patientRepository = patientRepository;
        this.auditService = auditService;
        checkClinicData(clinicName, clinicCnpj, dpoContact, environment);
        this.termText = fillClinicData(readFile(TERM_FILE), clinicName, clinicCnpj, dpoContact);
        this.researchText = fillClinicData(readFile(RESEARCH_FILE), clinicName, clinicCnpj, dpoContact);
    }

    // fora do desenvolvimento e do teste a api n sobe sem os tres dados da clinica, como sem JWT_SECRET e
    // PUBLIC_URL: no ar com colchetes, os primeiros pacientes aceitavam um termo sem a clinica e nunca mais
    // eram chamados a aceitar (revisao das regras R21). no dev e no teste ela sobe com os colchetes e avisa
    private void checkClinicData(String clinicName, String clinicCnpj, String dpoContact, Environment environment) {
        List<String> missing = new ArrayList<>();
        if (!hasText(clinicName)) {
            missing.add("CLINIC_NAME");
        }
        if (!hasText(clinicCnpj)) {
            missing.add("CLINIC_CNPJ");
        }
        if (!hasText(dpoContact)) {
            missing.add("CLINIC_DPO_CONTACT");
        }
        if (missing.isEmpty()) {
            return;
        }
        if (!environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalStateException("Falta preencher " + String.join(", ", missing) + ": sem elas o termo "
                    + "de consentimento sai com colchetes no lugar dos dados da clínica");
        }
        log.warn("{} vazio: os termos saem com colchetes no lugar dos dados da clínica", String.join(", ", missing));
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
        acceptance.setTermHash(hashOfTheTextShown(termType, termVersion));
        // menor de 18: o aceite eh do responsavel e o nome dele fica junto (LGPD art. 14)
        // adulto com cuidador aceita sozinho, entao ai fica nulo
        if (user instanceof Patient patient && patient.isMinor()) {
            if (!hasLegalGuardian(patient)) {
                throw new BusinessException(GUARDIAN_REQUIRED_MESSAGE);
            }
            acceptance.setGuardianName(patient.getGuardianName());
            acceptance.setGuardianRelationship(patient.getGuardianRelationship());
        }
        consentAcceptanceRepository.save(acceptance);
    }

    // o sha-256 do texto q a tela mostrou pra essa versao, ja com os dados da clinica no lugar dos colchetes
    // so a versao atual tem texto conhecido, entao a de outra versao fica sem (revisao das regras R21)
    private String hashOfTheTextShown(ConsentType termType, String termVersion) {
        if (termType == ConsentType.RESEARCH) {
            return RESEARCH_VERSION.equals(termVersion) ? SecureTokens.hashToken(researchText) : null;
        }
        return CURRENT_VERSION.equals(termVersion) ? SecureTokens.hashToken(termText) : null;
    }

    // paciente q aceitou uma versao antiga do termo do cadastro precisa aceitar a atual
    // e tbm qnd a idade cruzou os 18 anos dps do aceite, pela data corrigida ou pelo aniversario
    // conta sem aceite nenhum so existe em teste e seed, o cadastro sempre grava um
    public boolean needsNewAcceptance(Users user) {
        if (!(user instanceof Patient patient)) {
            return false;
        }
        if (hasCurrentAcceptance(patient)) {
            return false;
        }
        return consentAcceptanceRepository.existsByUserIdAndTermType(patient.getId(), ConsentType.REGISTRATION);
    }

    // termo pendente so pela faixa de idade: o texto atual ja foi aceito, mas por outra faixa
    // ai a data de nascimento errada ainda pode ser corrigida no perfil, sem gravar um aceite na faixa errada
    // a pendencia q veio do aniversario de 18 anos n entra: a data n mudou e n tem o q corrigir, e o
    // responsavel q ainda usa a conta n pode voltar a data pra fugir do aceite de adulto (revisao das regras R9)
    public boolean isPendingOnlyByAge(Users user) {
        if (!(user instanceof Patient patient)) {
            return false;
        }
        Optional<ConsentAcceptance> lastAcceptance = lastCurrentAcceptance(patient);
        return lastAcceptance.isPresent() && !isFromTheAgeBandOf(lastAcceptance.get(), patient)
                && !cameFromTheBirthday(lastAcceptance.get(), patient);
    }

    // aceite de menor q pela data de hoje ainda era de menor qnd foi feito, com a data igual desde o aceite:
    // quem mudou foi a idade. a data trocada no perfil dps do aceite pode ser o ano digitado errado, tipo 2008
    // no lugar de 2018, e ai a pendencia eh da data e ela ainda se corrige
    private boolean cameFromTheBirthday(ConsentAcceptance acceptance, Patient patient) {
        return hasText(acceptance.getGuardianName()) && !patient.isMinor()
                && Patient.isMinor(patient.getBirthDate(), acceptance.getAcceptedAt().toLocalDate())
                && !auditService.birthDateChangedAfter(patient.getId(), acceptance.getAcceptedAt());
    }

    // o ultimo aceite da versao atual precisa ser da faixa de idade de hoje
    private boolean hasCurrentAcceptance(Patient patient) {
        Optional<ConsentAcceptance> lastAcceptance = lastCurrentAcceptance(patient);
        return lastAcceptance.isPresent() && isFromTheAgeBandOf(lastAcceptance.get(), patient);
    }

    private Optional<ConsentAcceptance> lastCurrentAcceptance(Patient patient) {
        return consentAcceptanceRepository.findFirstByUserIdAndTermTypeAndTermVersionOrderByAcceptedAtDescIdDesc(
                patient.getId(), ConsentType.REGISTRATION, CURRENT_VERSION);
    }

    // o de menor vale com o responsavel junto e o de adulto eh do proprio paciente, sem responsavel
    private boolean isFromTheAgeBandOf(ConsentAcceptance acceptance, Patient patient) {
        boolean acceptedByGuardian = hasText(acceptance.getGuardianName());
        return acceptedByGuardian == patient.isMinor();
    }

    @Transactional
    public void acceptNewVersion(Long patientId, String termVersion, GuardianDTO guardian) {
        if (!isCurrentVersion(termVersion)) {
            throw new BusinessException(PatientService.OUTDATED_TERM_MESSAGE);
        }
        // o paciente vem do banco e n da sessao: salvar o objeto da sessao gravava por cima
        // o q mudou dps do login, como a desativacao pela administracao
        // e vem travado, na ordem da escala e da confirmacao do e-mail (paciente, dps conta):
        // o aceite e o responsavel gravam na conta antes do paciente
        patientRepository.lockById(patientId);
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> NotFoundException.account());
        // conta de menor sem responsavel salvo, ou com ele sem o parentesco (de antes da regra),
        // informa ele aqui pq com o termo pendente o PUT /profile fica travado
        if (patient.isMinor() && !hasLegalGuardian(patient)) {
            // sem responsavel no pedido vale o salvo, ai o erro aponta so o campo q falta
            GuardianDTO newGuardian = guardian == null ? GuardianDTO.from(patient) : guardian;
            GuardianDTO.checkLegalGuardian(newGuardian, patient.getBirthDate());
            GuardianDTO.applyTo(patient, newGuardian);
            patientRepository.save(patient);
        }
        // aceitar de novo a msm versao n duplica o registro, mas a idade q cruzou os 18 anos grava outro
        if (!hasCurrentAcceptance(patient)) {
            registerAcceptance(patient, termVersion);
        }
    }

    // o aceite do menor guarda o nome e o parentesco do responsavel, entao precisa dos dois salvos
    private boolean hasLegalGuardian(Patient patient) {
        return hasText(patient.getGuardianName()) && hasText(patient.getGuardianRelationship());
    }

    // so vale o aceite da versao atual do texto de pesquisa q n foi retirado
    // menor de 18 n entra nem com aceite antigo, defesa pra exportacao sem identificacao
    public boolean hasResearchConsent(Patient patient) {
        if (patient.isMinor()) {
            return false;
        }
        return consentAcceptanceRepository.existsByUserIdAndTermTypeAndTermVersionAndRevokedAtIsNull(
                patient.getId(), ConsentType.RESEARCH, RESEARCH_VERSION);
    }

    // msm regra do hasResearchConsent, senao o perfil e a exportacao discordam
    // a diferenca eh o menor: um aceite q ainda vale aparece aqui pra ele conseguir retirar no perfil
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
        if (patient.isMinor()) {
            throw new BusinessException(MINOR_RESEARCH_MESSAGE);
        }
        if (!hasResearchConsent(patient)) {
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

    private String fillClinicData(String text, String clinicName, String clinicCnpj, String dpoContact) {
        text = fillMarker(text, CLINIC_NAME_MARKER, clinicName);
        text = fillMarker(text, CLINIC_CNPJ_MARKER, clinicCnpj);
        text = fillMarker(text, DPO_CONTACT_MARKER, dpoContact);
        return text;
    }

    // sem a variavel o marcador fica no texto, pra teste e ambiente local subirem
    private String fillMarker(String text, String marker, String value) {
        if (!hasText(value)) {
            return text;
        }
        return text.replace(marker, value.trim());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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
