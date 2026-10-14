import {formatPhone, onlyDigits} from "./masks.js";

// familiar ou cuidador do paciente (RF38), tudo opcional
// os campos do formulario sao guardianName, guardianRelationship e guardianPhone

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
export function findGuardianErrors(formData) {
    const errors = {};
    const phone = onlyDigits(formData.guardianPhone);
    if (phone && phone.length < 10) {
        errors["guardian.phone"] = "Informe o telefone com DDD";
    }
    if (!formData.guardianName.trim() && (formData.guardianRelationship.trim() || phone)) {
        errors["guardian.name"] = "Informe o nome de quem acompanha";
    }
    return errors;
}
