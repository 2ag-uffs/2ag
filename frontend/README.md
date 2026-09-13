# frontend do sistema 2ag

interface web do sistema 2ag, que consome a api do [`backend`](../backend/README.md)

o documento de requisitos vigente é o [`docs/requisitos-v2.md`](../docs/requisitos-v2.md). este README descreve **o que existe hoje** — o que falta está na seção [limitações conhecidas](#limitações-conhecidas)

---

## tecnologias

* **react 19**
* **vite 6** como build e servidor de desenvolvimento
* **react router dom 7** pra navegação no lado do cliente
* **react-icons** pros ícones
* **css puro**, sem framework de estilo

---

## como rodar

1. **pré-requisitos**: node.js 18+ e npm

2. **instalação**:
   ```bash
   cd frontend
   npm install
   ```

3. **execução**:
   ```bash
   npm run dev
   ```

4. **acesso**: `http://localhost:5173`

o backend precisa estar rodando em `http://localhost:8080`, senão o login não funciona

### outros comandos

| comando | o que faz |
| :--- | :--- |
| `npm run dev` | sobe o servidor de desenvolvimento |
| `npm run build` | gera o build de produção em `dist/` |
| `npm run preview` | serve o build de produção localmente |
| `npm run lint` | roda o eslint — **quebrado hoje**, ver limitações |

---

## estrutura

```
src/
├── main.jsx                 ponto de entrada, importa os estilos globais
├── index.css
├── routes/
│   └── routes.jsx           todas as rotas da aplicação
├── styles/                  design system global
│   ├── colors.css           variáveis de cor em :root
│   ├── fonts.css
│   ├── button.css
│   └── input.css
├── components/              componentes reutilizáveis
│   ├── header/
│   ├── modal/
│   └── scale-selector/
└── pages/                   uma pasta por página, com .jsx e .css juntos
```

`public/` guarda os recursos estáticos: fontes, logotipos e imagens

### estilização

a abordagem é dupla, como descrito no relatório técnico:

1. um **design system global** em `src/styles/`, com variáveis css em `:root` pra garantir consistência visual
2. **css por componente**, um arquivo por página, pra evitar conflito de especificidade entre telas

### nomenclatura

segue o padrão definido no [README da raiz](../README.md#nomenclatura): arquivos e pastas em **kebab-case**, funções e variáveis em **camelCase**, componentes e tipos em **PascalCase**, constantes em **SCREAMING_SNAKE_CASE**

---

## páginas

### paciente

| rota | página |
| :--- | :--- |
| `/login` | login |
| `/sign-up` | cadastro de paciente |
| `/dashboard-paciente` | painel inicial |
| `/anamnese` | formulário de anamnese |
| `/acompanhamento-paciente` | ficha de acompanhamento semanal |
| `/pacientes/:patientId/escalas` | central de escalas |
| `/escala-hamilton` | escala de ansiedade de hamilton |
| `/diario-sono` | diário de sono |
| `/agendamento-consulta` | agendamento de consulta |
| `/historico-paciente` | histórico clínico |
| `/notificacoes-paciente` | notificações |
| `/perfil` | perfil do usuário |

### prescritor

| rota | página |
| :--- | :--- |
| `/dashboard-prescritor` | painel inicial |
| `/lista-paciente` | lista de pacientes |
| `/consulta` | consulta clínica |
| `/consulta/:appointmentId/prescricao` | nova prescrição, a partir de uma consulta |
| `/mini-exame` | mini-exame do estado mental |
| `/acompanhamento-prescritor` | acompanhamento do paciente |
| `/paciente/:pacienteId/selecao-escalas` | designar escalas |
| `/paciente/:pacienteId/historico` | histórico clínico do paciente |
| `/agendamento-prescritor` | agenda |
| `/notificacoes-prescritor` | notificações |
| `/dados-consultorio` | dados do consultório |

---

## autenticação

o login chama `POST /auth/login`, guarda o jwt em `localStorage` na chave `authToken` e decodifica o payload com `atob` pra descobrir o perfil do usuário:

* `ROLE_USER` → redireciona pra `/dashboard-paciente`
* `ROLE_ADMIN` → redireciona pra `/dashboard-prescritor`

toda chamada autenticada manda o header `Authorization: Bearer <token>`

---

## limitações conhecidas

levantadas na auditoria de 12/09/2026. cada item aponta o requisito da v2.0 que resolve

**configuração**

* **parcialmente resolvido**: existe o `src/services/api.js`, que lê o endereço de `VITE_API_URL`, injeta o token, trata 401 e expõe os erros por campo. `login.jsx`, `consulta-clinica.jsx` e `prescricao.jsx` já usam. faltam 12 arquivos (RNF15)
* o `eslint.config.js` está dentro de `public/`, então é publicado como arquivo estático e o eslint não encontra a configuração. na prática **`npm run lint` nunca rodou** neste projeto

**segurança**

* **não existe proteção de rota**. não há componente de rota protegida: qualquer url abre direto, inclusive `/dashboard-prescritor`. a separação entre perfis acontece só no redirecionamento depois do login (RF29, RF30)
* o token fica em `localStorage`, o que é vulnerável a xss
* a função `parseJwt` está duplicada em várias páginas e o perfil do usuário é lido do payload sem validação de assinatura — o que é aceitável pra decidir o que mostrar na tela, mas hoje é a única barreira que existe

**telas que faltam**

* ~~não existe tela de progresso~~ **resolvida**: `/progresso` para o paciente e `/paciente/:patientId/progresso` para o prescritor, as duas na mesma página (RF27, RF28). usa **recharts**
* o `acompanhamento-semanal-prescritor.jsx` e o histórico ainda não exibem gráfico (RF07)
* ~~três rotas comentadas e sem página: `/escala-pittsburgh`, `/diario-dor`, `/diario-tea`~~ **resolvido**: as três telas existem e salvam. **as 8 escalas do `ScaleType` agora têm rota** (RF08, RF23, RF24, RF25)
* não existe exportação de dados em pdf ou csv (RF33)

**telas que ainda não estão ligadas ao backend**

* `agendamento-consulta-prescritor.jsx` **não faz nenhuma chamada ao backend** — a agenda inteira é dado fixo no código
* `agendamento-consulta-paciente.jsx` chama `POST /consultas`, e a rota do backend é `/consulta`, no singular
* 12 arquivos ainda montam o `fetch` na mão com o endereço fixo. a migração pro `services/api.js` está em andamento
* **`mini-exame.jsx` não salva**: assim como acontecia no hamilton, ele calcula o total e mostra um `alert`, sem nunca chamar a api. o paciente (ou o prescritor) preenche e o dado não existe

**estrutura**

* não há rota de fallback (404), nem `ErrorBoundary`, nem layout compartilhado entre as páginas
* não há estado global de autenticação: cada página lê o token e decodifica por conta própria
* todo texto de interface está embutido no jsx, sem arquivo de dicionário, então não há como traduzir a interface (RNF12)
