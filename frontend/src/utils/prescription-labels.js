// textos das opcoes da prescricao do jeito q a tela mostra (RF05)

export const SPECTRUM_OPTIONS = [
    {value: "ISOLADO", label: "Isolado"},
    {value: "BROAD_SPECTRUM", label: "Broad spectrum"},
    {value: "FULL_SPECTRUM", label: "Full spectrum"},
];

export const CANNABINOID_OPTIONS = [
    {value: "CBD", label: "CBD"},
    {value: "THC", label: "THC"},
    {value: "CBG", label: "CBG"},
    {value: "CBN", label: "CBN"},
    {value: "CBC", label: "CBC"},
    {value: "THCV", label: "THCV"},
    {value: "CBDA", label: "CBDA"},
    {value: "OUTRO", label: "Outro"},
];

export const UNIT_OPTIONS = [
    {value: "PERCENTUAL", label: "%"},
    {value: "MG_POR_ML", label: "mg/mL"},
];

export const ADMINISTRATION_ROUTE_OPTIONS = [
    {value: "Sublingual", label: "Sublingual"},
    {value: "Oral", label: "Oral"},
    {value: "Tópica", label: "Tópica"},
];

export function labelOf(options, value) {
    const option = options.find((currentOption) => currentOption.value === value);
    return option ? option.label : value;
}

// resumo do oleo tipo CBD 10% + THC 0,3%
export function compositionSummary(components) {
    if (!components || components.length === 0) {
        return "";
    }
    return components
        .map((component) => {
            const amount = Number(component.concentration).toLocaleString("pt-BR");
            const unit = component.unit === "PERCENTUAL" ? "%" : " mg/mL";
            return labelOf(CANNABINOID_OPTIONS, component.cannabinoid) + " " + amount + unit;
        })
        .join(" + ");
}

export function prescriptionStatusLabel(prescription) {
    if (prescription.annulled) {
        return "Anulada";
    }
    return prescription.status === "VIGENTE" ? "Vigente" : "Substituída";
}
