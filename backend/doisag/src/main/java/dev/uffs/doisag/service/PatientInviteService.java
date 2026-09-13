package dev.uffs.doisag.service;

import dev.uffs.doisag.dto.InviteCreatedDTO;
import dev.uffs.doisag.dto.InviteInfoDTO;
import dev.uffs.doisag.infra.NotFoundException;
import dev.uffs.doisag.model.PatientInvite;
import dev.uffs.doisag.model.Prescriber;
import dev.uffs.doisag.repository.PatientInviteRepository;
import dev.uffs.doisag.repository.PrescriberRepository;
import dev.uffs.doisag.security.SecureTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// convite de cadastro de paciente (RN06)
// o prescritor gera um link aleatorio q vale pra um cadastro e por poucos dias
@Service
public class PatientInviteService {

    public static final String INVALID_INVITE_MESSAGE =
            "Convite inválido ou vencido. Peça um novo link ao seu prescritor.";

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

        return new InviteCreatedDTO(token, invite.getExpiresAt());
    }

    // o q a tela de cadastro mostra antes do formulario
    @Transactional(readOnly = true)
    public InviteInfoDTO getInviteInfo(String token) {
        PatientInvite invite = findUsableInvite(token);
        if (invite == null) {
            throw new NotFoundException(INVALID_INVITE_MESSAGE);
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
        if (invite == null || !invite.isUsableAt(LocalDateTime.now())) {
            return null;
        }
        if (!invite.getPrescriber().isActive()) {
            return null;
        }
        return invite;
    }
}
