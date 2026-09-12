# 2ag

Sistema livre para acompanhamento terapêutico longitudinal de pacientes em tratamento com óleo de *Cannabis sativa*.

O 2ag digitaliza o ciclo de cuidado de uma clínica que prescreve fitocanabinoides: triagem por anamnese, registro de consulta e prescrição, aplicação das escalas clínicas padronizadas com cálculo dos escores, envio automatizado dos formulários de acompanhamento ao longo dos 90 dias de tratamento e visualização gráfica da evolução dos sintomas.

[![Licença: AGPL v3](https://img.shields.io/badge/licen%C3%A7a-AGPL--3.0-blue.svg)](./LICENSE)

---

## ⚠️ Estado do projeto

**Em desenvolvimento. Não use com dados reais de paciente.**

Uma auditoria técnica realizada em setembro de 2026 identificou falhas abertas de controle de acesso, ausência de conformidade com a LGPD e escalas clínicas com escore calculado fora do algoritmo oficial dos instrumentos. A correção desses pontos é pré-requisito para qualquer uso com dado real, e está em andamento.

O documento de requisitos vigente é o [`docs/requisitos-v2.md`](./docs/requisitos-v2.md).

---

## Projeto de extensão universitária

Este sistema é desenvolvido como ação de extensão no componente **GCH1993 — Projeto de Integração de Extensão** da **Universidade Federal da Fronteira Sul (UFFS)**, campus Chapecó, sob orientação do Prof. Claunir Pavan.

**Parceiro:** Instituto EDMA — clínica de prática integrativa em Chapecó/SC, voltada à prescrição e ao acompanhamento terapêutico com fitocanabinoides. A demanda foi levantada junto à biomédica responsável pelos atendimentos e a pacientes em acompanhamento.

**Por que software livre:** a clínica parceira é o cliente de referência, não o beneficiário único. O sistema é publicado sob AGPL-3.0 para que qualquer clínica ou serviço de saúde com a mesma necessidade possa instalá-lo, adaptá-lo e operá-lo com autonomia sobre os próprios dados.

### Origem do código

O projeto partiu de um protótipo desenvolvido por Maiqueli Eduarda Dama Mingoti e Caroline de Quadros Piazza nos componentes **Engenharia de Software I** e **Programação II**, em 2025, que nunca foi implantado nem utilizado pela clínica. O histórico daquele protótipo permanece público em [maiqueli-mingoti/2ag](https://github.com/maiqueli-mingoti/2ag).

O que a ação de extensão entrega, e que não foi objeto dos componentes anteriores: adequação de segurança e conformidade com a LGPD, correção dos instrumentos clínicos, implantação na clínica, capacitação da equipe, execução de piloto com pacientes e avaliação do impacto sobre a rotina do serviço.

---

## Tecnologias

| Camada | Stack |
| :--- | :--- |
| Backend | Java 17, Spring Boot 3.5, Spring Data JPA, Spring Security (JWT) |
| Frontend | React 19, Vite 6 |
| Banco | PostgreSQL |
| Build | Maven Wrapper (backend), npm (frontend) |

Toda a pilha é software livre.

---

## Estrutura do repositório

```
backend/      api rest em spring boot
frontend/     interface web em react
database/     modelagem conceitual, logica e fisica
docs/         requisitos, escalas de referencia e registro da extensao
```

---

## Como executar

### Com Docker (recomendado)

Precisa apenas de Docker e Docker Compose instalados.

```bash
cp .env.example .env
# abra o .env e preencha POSTGRES_PASSWORD e JWT_SECRET
docker compose up
```

A interface fica em `http://localhost:5173` e a API em `http://localhost:8080`. O banco sobe junto, num volume que preserva os dados entre reinicializações.

Para gerar uma chave de assinatura de token:

```bash
openssl rand -base64 48
```

> `POSTGRES_PASSWORD` e `JWT_SECRET` não têm valor padrão de propósito: a aplicação não sobe sem eles, em vez de subir com credencial conhecida. O `.env` não é versionado.

### Sem Docker

Instalação manual do banco, backend e frontend: veja [`backend/README.md`](./backend/README.md) e [`database/README.md`](./database/README.md).

---

## Escalas clínicas implementadas

| Instrumento | Aplicação | Escore |
| :--- | :--- | :--- |
| Anamnese | paciente | — |
| Ficha de acompanhamento | paciente | 15 parâmetros de 0 a 10 |
| Escala de Ansiedade de Hamilton (HAM-A) | paciente | 0 a 56 |
| Índice de Qualidade do Sono de Pittsburgh (PSQI-BR) | paciente | 0 a 21, 7 componentes |
| Diário de sono | paciente | — |
| Registro de dor | paciente | 0 a 10 + interferência |
| Registro de sintomas (TEA) | paciente | frequência de comportamentos |
| Mini-Exame do Estado Mental (MEEM) | prescritor | 0 a 30 |

Os algoritmos de cálculo são normativos e estão especificados no Anexo A do [documento de requisitos](./docs/requisitos-v2.md).

---

## Privacidade

O sistema trata dado pessoal sensível de saúde, sujeito ao art. 11 da LGPD.

**Nunca versione neste repositório** formulário preenchido, planilha de acompanhamento, exportação de prontuário ou qualquer arquivo que identifique paciente. O `.gitignore` cobre os padrões mais comuns, mas a responsabilidade é de quem commita — confira o `git diff` antes de cada envio.

---

## Licença

[GNU Affero General Public License v3.0](./LICENSE).

A AGPL foi escolhida deliberadamente: como o 2ag é um sistema web, ela garante que quem oferecer uma versão modificada como serviço também publique suas modificações, mantendo as melhorias disponíveis para os serviços de saúde que adotarem o sistema.
