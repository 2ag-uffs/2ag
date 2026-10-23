# frontend do 2ag

interface web do 2ag, pensada primeiro para o celular. consome a api do [`backend`](../backend/README.md).

## tecnologias

- react 19 e vite 8
- react router 8, com cada tela carregada sob demanda
- recharts no gráfico de evolução
- react-icons nos ícones do menu
- css puro, com css modules nos componentes novos

## rodando

```bash
cd frontend
npm install
npm run dev
```

abra http://localhost:5173. o vite repassa as chamadas de `/api` para a api em `http://localhost:8080`, então a api precisa estar rodando. se ela estiver em outra porta, crie o arquivo `frontend/.env.local` com a linha `API_URL=http://localhost:8081`. esse arquivo fica fora do git.

| comando | o que faz |
| :--- | :--- |
| `npm run dev` | servidor de desenvolvimento |
| `npm run build` | build de produção em `dist/` |
| `npm run preview` | serve o build de produção |
| `npm run lint` | eslint |
| `npm test` | testes com vitest e testing library |

## estrutura

```
src/
├── main.jsx                  carrega a sessão e monta as rotas
├── app/
│   ├── router.jsx            todas as rotas, cada tela num arquivo baixado sob demanda
│   ├── route-guards.js       quem pode abrir cada rota
│   └── role-home.js          tela inicial de cada perfil
├── components/
│   ├── app-layout/           cabeçalho, menu e barra inferior no celular
│   ├── auth-layout/          moldura das telas de login e cadastro
│   ├── form/                 campos, campo de senha e regras de senha
│   ├── invite-patient-modal/ convite de paciente
│   ├── page-loader/          indicador de carregamento
│   ├── status-page/          página não encontrada e erro inesperado
│   ├── modal/
│   └── scale-selector/
├── pages/                    uma pasta por tela
├── services/api.js           cliente único da api
├── styles/                   cores, fontes e estilos globais
└── utils/                    máscaras de cpf e telefone e lista de estados
```

## navegação

- **layout por perfil:** paciente, prescritor e administrador têm menu próprio. no celular o menu vira uma barra inferior
- **rotas protegidas:** as regras ficam em `app/route-guards.js` e rodam antes da tela abrir. quem não tem acesso volta para a própria tela inicial, e quem não está logado vai para o login com `?voltar=` e, depois de entrar, volta para a tela que pediu. a api confere tudo de novo, porque esconder rota não protege dado
- **carregamento sob demanda:** cada tela é um arquivo separado. enquanto a próxima tela baixa, a atual continua visível e uma barra fina aparece no topo
- **transição:** cada tela nova entra com um fade curto, desligado para quem pede menos movimento no sistema operacional
- **rota inexistente e erro inesperado:** caem numa página própria, com caminho de volta ao início

## sessão

a sessão fica num cookie `httpOnly`, que o javascript não enxerga. o `main.jsx` pergunta à api quem está logado antes de montar as rotas, e `getLoggedUser()` devolve esse usuário para qualquer tela. um `401` com alguém logado manda de volta ao login. o paciente cujo aceite do termo de consentimento é de uma versão antiga vê o termo novo no lugar de qualquer tela até aceitar, e um `403` com `X-Term-Pending` abre esse aceite por cima da tela aberta. `setLoggedUser()` avisa o layout com o evento `logged-user-changed`, então o nome salvo no perfil aparece no menu na hora. como só a api apaga o cookie, `logout()` só esquece quem estava logado quando a api confirma a saída (um `401` também conta) e devolve se deu certo: se falhar, a tela avisa e a pessoa continua logada.

## datas

a api trabalha no horário de Brasília (`America/Sao_Paulo`) e as telas também. o hoje e o agora saem de `utils/date-format.js`, que usa o fuso de Brasília mesmo com o navegador em outro fuso: `toIsoDate(new Date())` é o dia de hoje em Brasília, e `isInTheFuture`, `hasEnded` e `minutesUntil` comparam o horário da consulta com o agora de Brasília. os horários aparecem como a api manda, e sempre que o fuso do navegador não é `America/Sao_Paulo`, mesmo outro de UTC-3 como `America/Fortaleza`, vai "(horário de Brasília)" do lado: `brasiliaTimeNote()` depois do horário nas telas de consultas e do painel do paciente, e `withBrasiliaTimeNote(texto)` nos avisos que já vêm com o horário no texto, no painel do paciente e na página de avisos, que o prescritor também vê. um dia montado pela tela (`addDays`, `mondayOf`, `new Date("2026-10-16T00:00:00")`) fica no começo do dia local, que onde o horário de verão pula a meia-noite é 1h.

## padrão de código

vale para todo código novo ou reescrito:

- arquivos e pastas em kebab-case, componentes em PascalCase, variáveis e funções em camelCase, tudo em inglês
- css modules (`nome.module.css`) em componente novo, para o estilo de uma tela não vazar para outra
- comentários curtos, em minúsculas, sem acento e sem pontuação
- texto que aparece na tela em português

## situação das telas

todas as telas seguem o padrão novo, cada uma com o seu css module. o css global que sobrou fica em `src/styles`: cores, fontes, botões, campos, avisos e os tokens de espaço e tamanho.
