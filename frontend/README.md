# frontend do 2ag

interface web do 2ag, pensada primeiro para o celular. consome a api do [`backend`](../backend/README.md).

## tecnologias

- react 19 e vite 8
- react router 8, com cada tela carregada sob demanda
- gráfico de evolução em svg feito à mão, sem biblioteca
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

a sessão fica num cookie `httpOnly`, que o javascript não enxerga. o `main.jsx` pergunta à api quem está logado antes de montar as rotas, e `getLoggedUser()` devolve esse usuário para qualquer tela. um `401` com alguém logado quer dizer que a sessão venceu (2 horas parada ou 12 horas depois do login): dentro do layout a página não recarrega, o `api.js` guarda o envio que recebeu o `401` só na memória e o layout abre um login por cima da tela (`components/login-again`), com o e-mail travado na conta aberta e só a senha para digitar. enquanto esse login está aberto, a tela de baixo continua montada mas fica escondida, como no aceite do termo, porque o computador pode ser de mais gente. ao entrar de novo, o envio vai outra vez com a sessão nova e a tela recebe a resposta de verdade, então uma escala ou anamnese pela metade não perde nada, e a tela só vê uma resposta demorada. se o termo ficou pendente enquanto a sessão estava vencida, o aceite aparece no lugar da tela logo depois de entrar, como no login normal. "Sair" nesse login descarta o envio e vai para a tela de login. confirmar o e-mail novo em outra aba também encerra a sessão, e aí o e-mail travado é o antigo, que não entra mais: quando a api recusa o login, a tela diz para tocar em "Sair" e entrar com o e-mail novo. nada do que foi digitado é gravado no navegador, que pode ser de mais gente. se a api devolvesse outra conta, nada é reenviado, essa conta sai e a página recarrega no login. fora do layout (o link de senha nova aberto com alguém logado) o `401` recarrega a página no login com `?sessao=expirada&voltar=`, como antes, e na própria tela de login o `401` continua sendo senha errada. o paciente cujo aceite do termo de consentimento é de uma versão antiga, ou de outra faixa de idade (a data de nascimento cruzou os 18 anos), vê o termo no lugar de qualquer tela até aceitar, e um `403` com `X-Term-Pending` abre esse aceite por cima da tela aberta. salvar no perfil a data de nascimento pergunta de novo à api (`checkPendingTerm()`, que lê o `GET /auth/me`) e abre o termo na hora quando ela cruzou os 18 anos. pendente só pela faixa de idade (`termPendingByAge`), a tela do termo mostra a data salva e deixa corrigir a data errada sem aceitar; com a data certa o termo fecha e a tela de baixo monta de novo, para o perfil não voltar com a data errada no formulário. `setLoggedUser()` avisa o layout com o evento `logged-user-changed`, então o nome salvo no perfil aparece no menu na hora. como só a api apaga o cookie, `logout()` só esquece quem estava logado quando a api confirma a saída (um `401` também conta) e devolve se deu certo: se falhar, a tela avisa e a pessoa continua logada.

## datas

a api trabalha no horário de Brasília (`America/Sao_Paulo`) e as telas também. o hoje e o agora saem de `utils/date-format.js`, que usa o fuso de Brasília mesmo com o navegador em outro fuso: `toIsoDate(new Date())` é o dia de hoje em Brasília, e `isInTheFuture`, `hasEnded` e `minutesUntil` comparam o horário da consulta com o agora de Brasília. os horários aparecem como a api manda, e sempre que o fuso do navegador não é `America/Sao_Paulo`, mesmo outro de UTC-3 como `America/Fortaleza`, vai "(horário de Brasília)" do lado: `brasiliaTimeNote()` depois do horário nas telas de consultas e do painel do paciente, e `withBrasiliaTimeNote(texto)` nos avisos que já vêm com o horário no texto, no painel do paciente e na página de avisos, que o prescritor também vê. um dia montado pela tela (`addDays`, `mondayOf`, `new Date("2026-10-16T00:00:00")`) fica no começo do dia local, que onde o horário de verão pula a meia-noite é 1h.

## escalas

a tela de escala (`pages/escala`) monta o formulário com a definição que vem da api. no diário a grade mostra os dias da tarefa aberta (no celular ela rola de lado e anda sozinha até o dia escolhido, sem mexer na rolagem da página), e o que a pessoa mexe fica guardado por dia (`editsByDay`) até salvar: o dia com rascunho aparece como *não salvo* na grade, **Salvar respostas** envia cada dia editado num `POST` próprio, em ordem de data, e o aviso diz quantos dias foram salvos. o dia que a api recusa continua como rascunho *não salvo* e o erro dele aparece na tela. sair da tela com rascunho pergunta antes, com o mesmo modal de confirmação da anamnese: o `useBlocker` do react router segura qualquer saída, seja o **Voltar** do formulário, a seta e o **Voltar** do layout ou o menu. quando a escala é a primeira tela da aba (aberta pelo endereço, por favorito ou por link colado, mesmo depois de recarregar ou de salvar com outra data), não há tela do app para onde voltar, então o **Voltar** do formulário leva à central de escalas, e o modal pergunta do mesmo jeito. já a escala aberta pelo app e depois recarregada ainda tem a tela de antes na aba, e o **Voltar** volta para ela: com rascunho, quem pergunta é o modal ou, se o navegador carregar aquela tela de novo, o próprio navegador. recarregar, fechar a aba ou sair do app pelo navegador não passam pelo router: com rascunho, o próprio navegador pergunta antes (`beforeunload`, em `utils/leave-warning.js`). trocar só a data na url continua na tela e não pergunta, e sair da conta também não, porque a sessão já acabou.

o paciente não vê o Mini-Exame do Estado Mental (decisão de 13/11/2026): a api deixa o exame de fora das listas dele e recusa com 403 o que ele pede direto, então as telas do paciente (central de escalas, histórico, resposta, progresso, impressão e painel) só mostram o que vem e nenhuma fala do exame. a tela de escala perdeu o aviso que só existia para o paciente que abria o endereço do mini-exame. o progresso só pede a série de um atributo que veio no catálogo, então o atributo do MEEM num link antigo nem chega na api e a tela abre no primeiro atributo do paciente. as telas do prescritor (mini-exame, histórico, progresso, exportação e impressão) continuam com o exame.

## padrão de código

vale para todo código novo ou reescrito:

- arquivos e pastas em kebab-case, componentes em PascalCase, variáveis e funções em camelCase, tudo em inglês
- css modules (`nome.module.css`) em componente novo, para o estilo de uma tela não vazar para outra
- comentários curtos, em minúsculas, sem acento e sem pontuação
- texto que aparece na tela em português

## situação das telas

todas as telas seguem o padrão novo, cada uma com o seu css module. o css global que sobrou fica em `src/styles`: cores, fontes, botões, campos, avisos e os tokens de espaço e tamanho.
