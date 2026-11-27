import {formatDate} from "./date-format.js";
import {isMinorPatient} from "./guardian.js";

// consentimento de pesquisa (RF37): os textos e a regra q o perfil e a tela do termo pendente usam iguais

export const RESEARCH_REVOKED_TEXT = "Consentimento retirado. Seus dados não entram em novas exportações para pesquisa.";

export function researchStatusText(consent) {
    if (consent.active) {
        return "Você aceitou participar da pesquisa em " + formatDate(consent.acceptedAt)
            + ". Pode retirar quando quiser.";
    }
    if (consent.termChanged) {
        return "O termo de pesquisa mudou desde o seu aceite. Para continuar participando, leia e aceite a versão nova.";
    }
    return "Você não participa da pesquisa. Participar é opcional e não muda o seu atendimento.";
}

// a pesquisa ainda n abre pra menor de 18, so aparece pra retirar um aceite q ja existe
export function showsResearch(profile) {
    return Boolean(profile.researchConsent
        && (!isMinorPatient(profile.birthDate) || profile.researchConsent.active));
}
