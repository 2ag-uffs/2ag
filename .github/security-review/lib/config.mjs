// tudo q controla modelo custo e limite fica aqui
// nada disso vem de variavel de ambiente nem do evento do github

export const MODEL = "claude-opus-5-5";
export const EFFORT = "high";
// o raciocinio do modelo tbm conta nesse teto
export const MAX_TOKENS = 32000;
export const BETAS = ["server-side-fallback-2026-07-01"];
export const FALLBACKS = "default";

export const ANTHROPIC_API_URL = "https://api.anthropic.com";
export const GITHUB_API_URL = "https://api.github.com";

// dolar por milhao de tokens do opus 5.5
export const PRICES = { input: 4, output: 20 };

export const MAX_CHUNK_CHARS = 100000;
// linha maior q isso n vai pra ia e o resto do arquivo vai com aviso no pr
export const MAX_LINE_CHARS = 5000;
export const MAX_MODEL_CALLS = 3;
export const MAX_FINDINGS = 15;
export const MAX_SECRET_FINDINGS = 15;
// cada valor de segredo eh procurado em toda linha q vai pra ia e acima disso nada vai
export const MAX_KNOWN_SECRETS = 200;
// o modelo pode mandar uma lista enorme e so os primeiros sao lidos
export const MAX_RAW_FINDINGS = 50;

export const TEXT_LIMITS = {
    title: 150,
    explanation: 1500,
    suggestion: 1000,
    summary: 2000,
    path: 200,
};

// o github recusa corpo acima de 65536 caracteres
export const MAX_POST_CHARS = 60000;
// a lista de arquivos do pr para em 3000 no github
export const MAX_LISTED_PR_FILES = 3000;

// tempo pra api comecar a responder e o stream em si segue
export const MODEL_TIMEOUT_MS = 5 * 60 * 1000;
export const MODEL_MAX_RETRIES = 2;
// o job morre em 15 min entao a ia para antes pra dar tempo de postar o resumo
export const MODEL_DEADLINE_MS = 12 * 60 * 1000;
export const GITHUB_TIMEOUT_MS = 30 * 1000;

export const ISSUE_LABEL = "revisao-de-seguranca";
export const FOOTER = "revisão automática por IA, pode errar: confira antes de mudar o código";
