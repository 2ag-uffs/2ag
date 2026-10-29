import {ageFrom} from "./date-format.js";
import {formatPhone, onlyDigits} from "./masks.js";

// familiar ou cuidador do paciente (RF38), opcional pra adulto
// menor de 18 precisa do responsavel legal, q participa do aceite do termo (LGPD art. 14 e Codigo Civil)
// os campos do formulario sao guardianName, guardianRelationship e guardianPhone

const ADULT_AGE = 18;
// menor de 16: o responsavel aceita em nome do paciente. com 16 e 17 o paciente aceita junto com ele
const ASSISTED_AGE = 16;
// ddd + os 8 numeros do fixo
const MIN_PHONE_DIGITS = 10;

// so muda o q a tela mostra, qm decide de vdd eh a api pela data de nascimento
// ageFrom conta anos completos igual a api: qm nasceu em 29/02 faz anos em 01/03
export function isMinorPatient(birthDate) {
    const age = ageFrom(birthDate);
    return age !== null && !Number.isNaN(age) && age < ADULT_AGE;
}

// botao q aceita o termo, no cadastro e na versao nova do termo
export const ACCEPT_BUTTON_TEXT = {
    "adult": "Li e aceito",
    "16-17": "Lemos e aceitamos",
    "under-16": "Li e aceito como responsável",
};

// faixa de idade q decide o texto do aceite: "adult", "16-17" ou "under-16"
export function ageBandOf(birthDate) {
    if (!isMinorPatient(birthDate)) {
        return "adult";
    }
    if (ageFrom(birthDate) >= ASSISTED_AGE) {
        return "16-17";
    }
    return "under-16";
}

export function guardianFieldsFrom(guardian) {
    return {
        guardianName: guardian?.name || "",
        guardianRelationship: guardian?.relationship || "",
        guardianPhone: formatPhone(guardian?.phone || ""),
    };
}

// td em branco vai como sem responsavel
export function guardianBody(formData) {
    const name = formData.guardianName.trim();
    const relationship = formData.guardianRelationship.trim();
    const phone = onlyDigits(formData.guardianPhone);
    if (!name && !relationship && !phone) {
        return null;
    }
    return {name: name, relationship: relationship || null, phone: phone || null};
}

// msm regra da api, com o nome do campo q ela usa
export function findGuardianErrors(formData, isMinor = false) {
    const errors = {};
    const phone = onlyDigits(formData.guardianPhone);
    if (phone && phone.length < MIN_PHONE_DIGITS) {
        errors["guardian.phone"] = "Informe o telefone com DDD";
    }
    if (isMinor) {
        if (!formData.guardianName.trim()) {
            errors["guardian.name"] = "Informe o nome do responsável legal";
        }
        if (!formData.guardianRelationship.trim()) {
            errors["guardian.relationship"] = "Informe o parentesco do responsável legal";
        }
        return errors;
    }
    if (!formData.guardianName.trim() && (formData.guardianRelationship.trim() || phone)) {
        errors["guardian.name"] = "Informe o nome de quem acompanha";
    }
    return errors;
}
