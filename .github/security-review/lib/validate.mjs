// confere na mao o json do modelo msm com o schema na api

import { MAX_FINDINGS, MAX_RAW_FINDINGS, TEXT_LIMITS } from "./config.mjs";

const LEVELS = ["high", "medium", "low"];

function clampText(value, maxLength) {
    const text = typeof value === "string" ? value.trim() : "";
    const chars = Array.from(text);
    return chars.length <= maxLength ? text : chars.slice(0, maxLength).join("");
}

// files eh o mapa caminho visto pelo modelo -> arquivo do diff
export function validateOutput(output, { files, ruleIds }) {
    const result = { findings: [], dropped: 0, summary: "", invalid: false };
    if (!output || typeof output !== "object" || !Array.isArray(output.findings)) {
        result.invalid = true;
        return result;
    }
    result.summary = clampText(output.summary, TEXT_LIMITS.summary);

    const items = output.findings.slice(0, MAX_RAW_FINDINGS);
    result.dropped += output.findings.length - items.length;

    for (const item of items) {
        if (!item || typeof item !== "object") {
            result.dropped++;
            continue;
        }
        const file = typeof item.file === "string" ? files.get(item.file) : undefined;
        const title = clampText(item.title, TEXT_LIMITS.title);
        if (!file || !ruleIds.includes(item.rule) || !LEVELS.includes(item.severity)
            || !LEVELS.includes(item.confidence) || title === "") {
            result.dropped++;
            continue;
        }
        result.findings.push({
            file: file.path,
            line: Number.isInteger(item.line) && item.line > 0 ? item.line : null,
            rule: item.rule,
            severity: item.severity,
            confidence: item.confidence,
            title,
            explanation: clampText(item.explanation, TEXT_LIMITS.explanation),
            suggestion: clampText(item.suggestion, TEXT_LIMITS.suggestion),
            deterministic: false,
        });
    }
    return result;
}

function levelOrder(level) {
    return LEVELS.indexOf(level);
}

export function sortFindings(findings) {
    return [...findings].sort((first, second) => {
        const bySeverity = levelOrder(first.severity) - levelOrder(second.severity);
        if (bySeverity !== 0) {
            return bySeverity;
        }
        return levelOrder(first.confidence) - levelOrder(second.confidence);
    });
}

// confianca baixa so conta no resumo e o resto passa do teto de 15
export function selectFindings(findings) {
    const sorted = sortFindings(findings);
    const postable = sorted.filter((finding) => finding.confidence !== "low");
    return {
        posted: postable.slice(0, MAX_FINDINGS),
        overLimit: Math.max(0, postable.length - MAX_FINDINGS),
        lowConfidence: sorted.length - postable.length,
    };
}

export function isMediumOrHigher(finding) {
    return finding.severity === "high" || finding.severity === "medium";
}
