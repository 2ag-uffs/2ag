# plano de testes

para quem testa o 2ag antes do piloto: como subir o ambiente, quais contas usar, os fluxos de cada perfil com o que deve acontecer e como relatar o que der errado.

cada fluxo tem um código (`CT-01`, `CT-02`...) e o requisito que ele confere em [`docs/requisitos-v2.md`](../requisitos-v2.md). o código vai no relato de bug, assim quem corrige sabe exatamente o que foi testado.

---

## ambiente

suba a api com o banco em memória e os dados de demonstração, como está no [README](../../README.md#sem-postgres-só-para-olhar-as-telas), e depois o front com `npm run dev`. abra http://localhost:5173.

- **reiniciar a api apaga tudo e recria os dados.** use isso para repetir um teste do zero.
- os e-mails (senha nova, convite) não saem de verdade: o texto aparece no log da api.
- teste no Chrome e no Firefox atuais, em três larguras: celular (375 px), tablet (768 px) e computador (1280 px ou mais). as ferramentas de desenvolvedor do navegador simulam as larguras.
- para usar dois perfis ao mesmo tempo, abra uma janela normal e outra anônima.

### contas

todas com a senha `Senha@123`, que só existe na instalação de desenvolvimento.

| conta | quem é | o que tem |
| :--- | :--- | :--- |
| `prescritor@email.com` | Ana Lima, prescritora | quatro pacientes ativos, uma arquivada, agenda com horários |
| `carlos.pereira@email.com` | Carlos Pereira, prescritor | nenhum paciente. serve para testar isolamento |
| `paciente@email.com` | Maria Souza | dois meses de diário, duas consultas, prescrição trocada, escalas pendentes |
| `joao.almeida@email.com` | João Pedro Almeida | pedido de consulta esperando resposta |
| `renata.dias@email.com` | Renata Dias Carvalho | escala de Hamilton vencida e um registro anulado |
| `paulo.nunes@email.com` | Paulo Nunes | paciente novo com a anamnese pendente |
| `carla.menezes@email.com` | Carla Menezes | arquivada depois da alta |
| `admin@email.com` | administração | contas de prescritor e auditoria |

---

## como relatar

- **um problema por issue**, pelo modelo **Relato de bug** em *Issues → New issue*. marque também o label `qa`.
- escreva os passos numerados a partir do login, o que deveria acontecer e o que aconteceu. print ou vídeo curto ajudam muito.
- diga a conta, o navegador e a largura da tela.
- **nunca use dado real de paciente**, nem em teste, nem em print.

gravidade:

| gravidade | quando usar |
| :--- | :--- |
| bloqueia | não dá para continuar o fluxo |
| grave | dado clínico errado, perdido ou visto por quem não deveria |
| média | funciona, mas só com um contorno |
| leve | visual, texto, alinhamento |

---

## 1. acesso e conta

requisitos RF01, RF02, RF18, RF35, RF36 e RN06.

| código | passos | o que deve acontecer |
| :--- | :--- | :--- |
| CT-01 | entrar com cada uma das contas | cada perfil cai no próprio painel: paciente, prescritor ou administração |
| CT-02 | errar a senha 5 vezes seguidas e tentar de novo com a senha certa. repetir com um e-mail sem cadastro | mensagem clara a cada erro e, na tentativa seguinte, aviso de bloqueio por 15 minutos, mesmo com a senha certa. o e-mail sem cadastro recebe exatamente as mesmas mensagens |
| CT-03 | sair da conta e usar o botão voltar do navegador | volta para a tela de entrada, sem reabrir tela protegida |
| CT-04 | em *esqueci minha senha*, pedir o link, abrir o endereço que aparece no log da api e criar senha nova. depois, como administração, usar *Senha nova* na conta de uma prescritora, errando e acertando a senha do administrador | entra com a senha nova e o mesmo link não serve uma segunda vez. na administração, a senha errada não gera link, a certa mostra o endereço para copiar e a ação aparece em *Auditoria* |
| CT-05 | como prescritora, gerar convite de paciente e abrir o link na janela anônima | cadastro pede o aceite do termo de consentimento e a conta nasce ligada à prescritora. link usado ou vencido não serve |
| CT-06 | em *Perfil*, mudar dados pessoais, depois e-mail e depois senha | dados salvos. troca de e-mail e de senha pedem a senha atual e a senha nova segue a regra de senha forte |

## 2. permissões e isolamento

requisitos RF29, RF30 e RF31.

| código | passos | o que deve acontecer |
| :--- | :--- | :--- |
| CT-07 | logado como paciente, digitar `/painel-prescritor` e `/lista-paciente` no endereço | volta para o painel da paciente |
| CT-08 | logado como prescritora, digitar `/administracao` | volta para o painel da prescritora |
| CT-09 | logado como Carlos Pereira, abrir o histórico de uma paciente da Ana trocando o número em `/paciente/3/historico` | acesso negado, sem mostrar nenhum dado |
| CT-10 | logado como Maria, trocar o número do endereço `/pacientes/3/escalas` pelo de outro paciente | acesso negado |
| CT-11 | abrir um prontuário e depois ver *Histórico de acesso* dele. como administração, abrir *Auditoria* | a abertura aparece com quem e quando. a auditoria da administração não mostra nome de paciente nem conteúdo clínico |

## 3. agenda

requisitos RF10 e RF11.

| código | passos | o que deve acontecer |
| :--- | :--- | :--- |
| CT-12 | em *Horários de atendimento*, mudar a duração e adicionar e remover períodos | a agenda e os horários livres da paciente passam a seguir os períodos novos |
| CT-13 | como paciente, pedir um horário livre com motivo | o pedido fica aguardando e o horário deixa de aparecer como livre |
| CT-14 | como prescritora, confirmar um pedido e recusar outro | quem foi confirmado recebe aviso. o horário recusado volta a ficar livre |
| CT-15 | remarcar e cancelar uma consulta como prescritora. como paciente, cancelar uma consulta com mais e com menos de 24 horas. como prescritora, marcar falta numa consulta cujo horário já terminou | a prescritora remarca e cancela. a paciente só cancela até 24 horas antes. o botão de falta só aparece depois que o horário termina, a paciente recebe aviso e o horário volta para a agenda. registrar o atendimento depois desfaz a falta |
| CT-16 | marcar consulta direto em *Nova consulta* | aparece na agenda como agendada e a paciente recebe aviso |

## 4. atendimento

requisitos RF04, RF05, RF12, RF13 e RF19.

| código | passos | o que deve acontecer |
| :--- | :--- | :--- |
| CT-17 | registrar a consulta de hoje de um paciente | registro aparece no histórico da prescritora e da paciente |
| CT-18 | emitir prescrição com dois canabinoides, concentração e escalonamento | a nova fica vigente e a anterior aparece como substituída |
| CT-19 | anular uma consulta e uma prescrição | o motivo é obrigatório e o registro continua visível, marcado como anulado |
| CT-20 | como Paulo, preencher e enviar a anamnese, depois corrigir. como prescritora, abrir o histórico dele | a prescritora vê a versão corrigida |
| CT-21 | arquivar um paciente e depois reativar | some da lista de ativos e aparece em arquivados com o histórico inteiro. o acompanhamento automático é encerrado |

## 5. escalas

requisitos RF06, RF08, RF09, RF20 a RF26, RF32, RN09 e RN10.

| código | passos | o que deve acontecer |
| :--- | :--- | :--- |
| CT-22 | como prescritora, enviar uma escala avulsa | aparece em *Escalas* da paciente com o prazo, e ela recebe aviso |
| CT-23 | no acompanhamento semanal, preencher um dia, trocar de dia e voltar sem salvar, depois salvar | o que foi digitado continua ao voltar. item deixado em branco não vira zero |
| CT-24 | corrigir uma resposta ainda não analisada. depois a prescritora marca como analisada e a paciente tenta corrigir de novo | antes da análise corrige. depois, a tela avisa que precisa falar com a prescritora |
| CT-25 | responder Hamilton e Pittsburgh com valores conhecidos | escore e interpretação batem com a conta feita na ficha em papel de [`docs/scales`](../scales) |
| CT-26 | preencher o diário do sono | tempos calculados certos, também quando dormir passa da meia-noite. a programação da prescritora aparece no topo |
| CT-27 | aplicar o MEEM numa consulta, com escolaridades diferentes | o corte muda com a escolaridade e o MEEM nunca aparece como tarefa da paciente |
| CT-28 | criar e depois encerrar um acompanhamento automático | as escalas escolhidas e as frequências aparecem certas. encerrar para os envios |
| CT-29 | anular uma resposta de escala | motivo obrigatório. a resposta fica marcada como anulada, com o motivo |
| CT-30 | olhar o painel da prescritora | a escala vencida da Renata aparece em *Escalas vencidas* |

## 6. progresso

requisitos RF07, RF27 e RF28.

| código | passos | o que deve acontecer |
| :--- | :--- | :--- |
| CT-31 | como prescritora, abrir *Progresso*, escolher paciente, trocar escala, item e período, e trocar de paciente | o gráfico acompanha cada troca e os filtros continuam ao trocar de paciente |
| CT-32 | ver um período com dias sem resposta, com consultas e com as gotas do dia marcadas | dia sem resposta fica sem ponto, nunca como zero. consultas aparecem como linha e as gotas como segunda linha |
| CT-33 | como paciente, abrir *Progresso* | vê o próprio gráfico, sem a opção *todo o tempo* |

## 7. avisos, exportação e impressão

requisitos RF14, RF15, RF33 e RF34.

| código | passos | o que deve acontecer |
| :--- | :--- | :--- |
| CT-34 | abrir *Avisos*, abrir um aviso, marcar todos como lidos e apagar um | cada aviso leva à tela certa e a contagem de não lidos atualiza |
| CT-35 | exportar cada CSV do histórico e abrir no Excel ou LibreOffice | colunas certas, acentos legíveis e datas no formato brasileiro. no modo anônimo não aparece nome nem CPF |
| CT-36 | abrir a impressão do histórico e salvar em PDF | o PDF sai sem menu, botões ou cortes no meio dos cartões |

> os lembretes automáticos (RF34) rodam uma vez por dia e ficam desligados no ambiente de teste local. eles entram no teste do servidor.

## 8. administração

| código | passos | o que deve acontecer |
| :--- | :--- | :--- |
| CT-37 | cadastrar prescritor com CPF inválido, depois com dados certos, e desativar e reativar | CPF inválido é recusado com mensagem. prescritor desativado não consegue entrar |

## 9. telas, celular e acessibilidade

| código | passos | o que deve acontecer |
| :--- | :--- | :--- |
| CT-38 | passar por todas as telas de cada perfil em 375 px, 768 px e 1280 px | nada corta nem cria rolagem para o lado. no celular o menu fica embaixo |
| CT-39 | usar os formulários principais só com o teclado | a ordem do tab faz sentido e o foco é sempre visível |
| CT-40 | abrir telas com a internet lenta (ferramentas do navegador) e desligar a api no meio | esqueleto enquanto carrega e mensagem clara quando o servidor não responde |
