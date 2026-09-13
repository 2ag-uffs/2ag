# frontend do 2ag

interface web do 2ag, pensada primeiro para o celular. consome a api do [`backend`](../backend/README.md).

## tecnologias

- react 19 e vite 6
- react router 7, com cada tela carregada sob demanda
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
│   ├── page-loader/          indicador de carregamento
│   ├── status-page/          página não encontrada e erro inesperado
│   ├── modal/
│   └── scale-selector/
├── pages/                    uma pasta por tela
├── services/api.js           cliente único da api
└── styles/                   cores, fontes e estilos globais
```

## navegação

- **layout por perfil:** paciente, prescritor e administrador têm menu próprio. no celular o menu vira uma barra inferior
- **rotas protegidas:** as regras ficam em `app/route-guards.js` e rodam antes da tela abrir. quem não tem acesso volta para a própria tela inicial. a api confere tudo de novo, porque esconder rota não protege dado
- **carregamento sob demanda:** cada tela é um arquivo separado. enquanto a próxima tela baixa, a atual continua visível e uma barra fina aparece no topo
- **transição:** cada tela nova entra com um fade curto, desligado para quem pede menos movimento no sistema operacional
- **rota inexistente e erro inesperado:** caem numa página própria, com caminho de volta ao início

## sessão

a sessão fica num cookie `httpOnly`, que o javascript não enxerga. o `main.jsx` pergunta à api quem está logado antes de montar as rotas, e `getLoggedUser()` devolve esse usuário para qualquer tela. um `401` com alguém logado manda de volta ao login.

## padrão de código

vale para todo código novo ou reescrito:

- arquivos e pastas em kebab-case, componentes em PascalCase, variáveis e funções em camelCase, tudo em inglês
- css modules (`nome.module.css`) em componente novo, para o estilo de uma tela não vazar para outra
- comentários curtos, em minúsculas, sem acento e sem pontuação
- texto que aparece na tela em português

## situação das telas

o layout, as rotas e a tela do administrador já seguem o padrão novo. as demais telas, incluindo login e cadastro, ainda são as de 2025 e estão sendo reescritas junto com cada requisito, na ordem do §8.6 do [documento de requisitos](../docs/requisitos-v2.md). até lá, o css delas continua global.
