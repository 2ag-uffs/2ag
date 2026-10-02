package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.InviteCreatedDTO;
import dev.uffs.doisag.dto.InviteInfoDTO;
import dev.uffs.doisag.dto.OpenInviteDTO;
import dev.uffs.doisag.infra.BusinessException;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.Patient;
import dev.uffs.doisag.model.PatientInvite;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientInviteRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// convite de cadastro de paciente (RN06)
// o prescritor gera um link aleatorio q vale pra um cadastro e por poucos dias
@Service
public class PatientInviteService {

    public static final String INVALID_INVITE_MESSAGE =
            "Convite inválido ou vencido. Peça um novo link ao seu prescritor.";
    public static final String INVITE_NOT_FOUND_MESSAGE = "Convite não encontrado";
    public static final String INVITE_ALREADY_USED_MESSAGE =
            "Este convite já foi usado: o paciente criou a conta. Se não era a pessoa certa, arquive ou peça para a administração desativar a conta";

    private static final int VALID_DAYS = 7;

    private final PatientInviteRepository patientInviteRepository;
    private final PrescriberRepository prescriberRepository;

    public PatientInviteService(PatientInviteRepository patientInviteRepository,
                                PrescriberRepository prescriberRepository) {
        this.patientInviteRepository = patientInviteRepository;
        this.prescriberRepository = prescriberRepository;
    }

    // cria o convite e devolve o codigo q vai no link
    @Transactional
    public InviteCreatedDTO createInvite(Long prescriberId) {
        String token = SecureTokens.createRandomToken();
        LocalDateTime now = LocalDateTime.now();

        PatientInvite invite = new PatientInvite();
        invite.setPrescriber(prescriberRepository.getReferenceById(prescriberId));
        invite.setTokenHash(SecureTokens.hashToken(token));
        invite.setCreatedAt(now);
        invite.setExpiresAt(now.plusDays(VALID_DAYS));
        patientInviteRepository.save(invite);

        return new InviteCreatedDTO(invite.getId(), token, invite.getExpiresAt());
    }

    // os convites q ainda podem ser usados, pro prescritor saber o q esta na rua
    @Transactional(readOnly = true)
    public List<OpenInviteDTO> listOpenInvites(Long prescriberId) {
        return patientInviteRepository
                .findByPrescriberIdAndUsedAtIsNullAndCancelledAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        prescriberId, LocalDateTime.now())
                .stream()
                .map(OpenInviteDTO::new)
                .toList();
    }

    // o link q foi pro numero errado deixa de valer antes de alguem usar
    // convite de outro prescritor responde como se n existisse
    @Transactional
    public void cancelInvite(Long inviteId, Long prescriberId) {
        PatientInvite invite = patientInviteRepository.findByIdForUpdate(inviteId)
                .filter(found -> found.getPrescriber().getId().equals(prescriberId))
                .orElseThrow(() -> NotFoundException.forUser(INVITE_NOT_FOUND_MESSAGE));
        if (invite.getUsedAt() != null) {
            throw new BusinessException(INVITE_ALREADY_USED_MESSAGE);
        }
        if (invite.getCancelledAt() == null) {
            invite.setCancelledAt(LocalDateTime.now());
        }
    }

    // o q a tela de cadastro mostra antes do formulario
    @Transactional(readOnly = true)
    public InviteInfoDTO getInviteInfo(String token) {
        PatientInvite invite = findUsableInvite(token);
        if (invite == null) {
            throw NotFoundException.forUser(INVALID_INVITE_MESSAGE);
        }

        Prescriber prescriber = invite.getPrescriber();
        return new InviteInfoDTO(prescriber.getName(), prescriber.getProfession(), invite.getExpiresAt());
    }

    // convite inexistente vencido usado ou de prescritor desativado volta null
    // precisa ser chamado dentro de uma transacao pq le o prescritor do convite
    public PatientInvite findUsableInvite(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        PatientInvite invite = patientInviteRepository.findByTokenHash(SecureTokens.hashToken(token)).orElse(null);
        if (isUsable(invite)) {
            return invite;
        }
        return null;
    }

    // mesma busca do cadastro mas travando o convite ate a transacao terminar
    public PatientInvite lockUsableInvite(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        PatientInvite invite = patientInviteRepository.findByTokenHashForUpdate(SecureTokens.hashToken(token))
                .orElse(null);
        if (isUsable(invite)) {
            return invite;
        }
        return null;
    }

    // o convite passa a pertencer ao paciente q acabou de se cadastrar
    public void markAsUsed(PatientInvite invite, Patient patient) {
        invite.setUsedAt(LocalDateTime.now());
        invite.setPatient(patient);
    }

    private boolean isUsable(PatientInvite invite) {
        if (invite == null || !invite.isUsableAt(LocalDateTime.now())) {
            return false;
        }
        return invite.getPrescriber().isActive();
    }
}
