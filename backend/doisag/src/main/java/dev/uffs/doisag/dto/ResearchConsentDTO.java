package dev.uffs.doisag.dto;

import java.time.LocalDateTime;

// situacao do consentimento de pesquisa do paciente (RF37)
// termChanged: aceitou uma versao antiga q n foi retirada, e o texto mudou dps
public record ResearchConsentDTO(boolean active, LocalDateTime acceptedAt, boolean termChanged) {
}
