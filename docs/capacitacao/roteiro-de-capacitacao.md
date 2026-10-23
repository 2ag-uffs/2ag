# roteiro de capacitação

Roteiro de uma sessão de cerca de 1h30 para apresentar o 2AG ao prescritor e à administração da clínica. A sessão usa a versão de demonstração, com dados inventados, e segue o caminho real de um paciente: da criação da conta do prescritor até o arquivamento do paciente.

Cada passo diz o que mostrar, onde clicar (com o texto exato dos botões e menus), o que conferir na tela e o que fazer se algo sair diferente.

> **Nunca use dado real de paciente na capacitação**, nem em print, nem em arquivo exportado. Nomes, CPFs e e-mails da demonstração são inventados.

---

## antes da sessão

### subir a demonstração

O jeito mais simples é a API com o banco em memória e os dados de demonstração, como explica o [README](../../README.md#sem-postgres-só-para-olhar-as-telas). Não precisa de postgres nem de docker.

Num terminal, a API:

```bash
cd backend/doisag
./mvnw spring-boot:test-run "-Dspring-boot.run.arguments=--spring.profiles.active=test --api.seed.enabled=true --api.session.secure-cookie=false"
```

Em outro terminal, a tela:

```bash
cd frontend
npm install
npm run dev
```

Abra http://localhost:5173 no navegador. Se a porta 8080 já estiver ocupada no computador, o README explica como subir a API em outra porta.

Quem já usa o docker pode seguir a seção *rodando com docker* do README, com `SEED_DADOS_TESTE=true` no `.env`, e abrir https://localhost. Nesse caso os dados ficam guardados ao parar (recomeçar do zero pede `docker compose down -v`) e o link dos e-mails não aparece no log. Para a capacitação, o banco em memória é mais prático.

Bom saber sobre essa demonstração:

- **Reiniciar a API apaga tudo e cria os dados de novo.** Ensaie a sessão no dia anterior e reinicie a API antes de começar, para partir do zero.
- **As datas dos dados fictícios contam a partir do dia em que a API sobe.** Suba no dia da sessão para o painel mostrar consultas de hoje e escalas no prazo.
- **Nenhum e-mail sai de verdade.** O link de primeiro acesso e o de **Senha nova** do prescritor aparecem na tela da administração. Os outros e-mails, como o de **Esqueci minha senha**, aparecem com o link no terminal da API.
- **As tarefas automáticas do dia não rodam na demonstração.** No servidor de verdade elas rodam toda manhã, às 8h: lembretes de consulta e de formulário, envio das rodadas seguintes do acompanhamento e fechamento das escalas que passaram do prazo. Na demonstração só a primeira rodada do acompanhamento sai, na hora em que ele é criado.

### contas da demonstração

Todas usam a mesma senha, que está na tabela *contas de teste* do README. Ela só existe na instalação de desenvolvimento.

| conta | quem é | para que serve na sessão |
| :--- | :--- | :--- |
| `admin@email.com` | administração | criar a conta do prescritor e ver a auditoria |
| `prescritor@email.com` | Ana Lima, prescritora | ler gráficos, exportar e ver a trilha com dados de dois meses |
| `paciente@email.com` | Maria Souza | dois meses de ficha diária, mostrar a grade com os dias do período |
| `joao.almeida@email.com` | João Pedro Almeida | aceitou a pesquisa: mostra a exportação sem identificação |
| `renata.dias@email.com` | Renata Dias Carvalho | escala vencida, registro anulado e o filho como familiar de contato |
| `paulo.nunes@email.com` | Paulo Nunes, com o nome social Paula Nunes | paciente novo com a avaliação inicial pendente |
| `carla.menezes@email.com` | Carla Menezes | paciente arquivada depois da alta |

### separar os dados inventados

Deixe anotado, para não improvisar na frente das pessoas:

- **Prescritora de treino:** nome "Prescritora Treino", e-mail `treino.prescritora@email.com`, um CPF fictício válido, profissão e número de registro inventados.
- **Paciente adulto:** nome "Paciente Adulto Treino", nome social "Sam Treino", e-mail `treino.adulto@email.com`, nascimento de uns 40 anos atrás.
- **Paciente menor:** nome "Paciente Menor Treino", e-mail `treino.menor@email.com`, nascimento de uns 12 anos atrás, responsável "Responsável Treino", parentesco "Mãe".
- **Uma senha de treino** que cumpra as regras da tela (de 8 a 64 caracteres, uma letra maiúscula, um número e um símbolo).

Cada conta precisa de um e-mail e de um CPF diferentes. O sistema confere os dígitos do CPF: use números de um gerador de CPF para testes, nunca o CPF de alguém de verdade.

### preparar o computador

- **Duas janelas do navegador:** uma normal, para a equipe (administração e prescritor), e uma anônima, para o paciente. Assim as duas pessoas ficam logadas ao mesmo tempo.
- **Links de primeiro acesso e de convite abrem mesmo com alguém logado naquela janela.** A tela avisa *Você está conectado como* com o nome da conta aberta e oferece **Sair e continuar** (no convite, **Sair e continuar o cadastro**): sai dessa conta e segue com o mesmo link, sem precisar colar de novo. **Voltar para a conta aberta** desiste do link.
- Para trocar de pessoa na mesma janela, use **Sair** (o ícone ao lado do nome, embaixo do menu; no celular, no alto da tela) e entre com a outra conta.
- Se quiser mostrar o celular, abra as ferramentas de desenvolvedor do navegador e escolha uma largura de celular. No celular o menu vai para a barra de baixo.

---

## agenda da sessão

| passo | assunto | tempo | quem conduz |
| :--- | :--- | :--- | :--- |
| 1 | abertura | 5 min | equipe do projeto |
| 2 | conta do prescritor e link de primeiro acesso | 6 min | administração |
| 3 | primeiro acesso do prescritor | 5 min | prescritor |
| 4 | horários de atendimento e folgas | 7 min | prescritor |
| 5 | convite para o paciente | 4 min | prescritor |
| 6 | cadastro do paciente adulto e do menor | 12 min | paciente |
| 7 | consulta, prescrição e agenda | 12 min | prescritor e paciente |
| 8 | escalas e acompanhamento automático de 90 dias | 8 min | prescritor |
| 9 | o paciente responde as escalas e a ficha diária | 8 min | paciente |
| 10 | gráficos de progresso e comentários | 6 min | prescritor |
| 11 | exportação | 5 min | prescritor |
| 12 | trilha de auditoria | 4 min | prescritor e administração |
| 13 | arquivar paciente | 3 min | prescritor |
| 14 | perguntas e checklist | 5 min | todos |

---

## 1. abertura (5 min)

- **Mostrar:** a tela **Entrar** em http://localhost:5173 e os três perfis do sistema: administração (cuida só das contas e não vê prontuário), prescritor (cuida dos pacientes) e paciente (responde as escalas, pede consulta e acompanha o próprio progresso).
- **Onde clicar:** nada ainda. Explique que tudo o que aparecer é inventado e que reiniciar a demonstração apaga o que for feito hoje.
- **Conferir:** todos enxergam a tela e sabem qual janela é da equipe e qual é do paciente.
- **Se der errado:** se, ao clicar em **Entrar**, a tela mostrar só *Ocorreu um erro.* e o terminal do `npm run dev` mostrar `http proxy error`, a tela não alcançou a API: ela não subiu, ainda está subindo ou subiu em outra porta. Confira o terminal da API e tente de novo quando ela terminar de subir.

## 2. a administração cria a conta do prescritor (6 min)

Janela normal, conta `admin@email.com`.

- **Mostrar:** a tela **Prescritores**, que é a primeira da administração. Ela lista quem atende na clínica, com o selo **Ativo** ou **Desativado**.
- **Onde clicar:**
  1. **Novo prescritor**.
  2. Preencha **Nome completo**, **E-mail**, **CPF**, **Profissão**, **Conselho** e **Número do registro**. **Data de nascimento (opcional)** e **Telefone (opcional)** podem ficar em branco. Mostre o aviso no fim do formulário: *Você não escolhe a senha. A pessoa recebe um link e cria a própria senha no primeiro acesso.*
  3. **Criar conta**.
  4. Na janela **Conta criada**, clique em **Copiar link** e depois em **Fechar**.
- **Conferir:**
  - A janela **Conta criada** diz que o link de primeiro acesso vale 48 horas e serve uma vez só.
  - A prescritora nova aparece na lista com os botões **Senha nova**, **Corrigir e-mail** e **Desativar**.
  - No servidor de verdade, com e-mail configurado, o link vai direto para o e-mail da pessoa em vez de aparecer na tela.
- **Se der errado:**
  - *O CPF informado é inválido*: use um CPF fictício com dígitos válidos.
  - O link venceu ou se perdeu: **Senha nova**, digite a senha da administração em **Sua senha de administrador** e **Gerar link**. Esse link vale só 30 minutos, e não 48 horas como o de primeiro acesso: entregue e use na hora.
  - O e-mail foi digitado errado: **Corrigir e-mail** (só aparece enquanto a pessoa ainda não criou a senha).
  - **Desativar** tira o acesso na hora e encerra o acompanhamento automático dos pacientes daquele prescritor. Não clique na sessão sem explicar isso.

## 3. primeiro acesso do prescritor (5 min)

Janela anônima, sem ninguém logado.

- **Mostrar:** a pessoa criando a própria senha pelo link.
- **Onde clicar:**
  1. Cole o link copiado no passo 2. Abre a tela **Criar senha nova**.
  2. Preencha **Nova senha** e **Repita a nova senha**. As regras ficam marcadas conforme a senha é digitada.
  3. **Salvar senha nova**.
  4. Na tela **Entrar**, use o e-mail da prescritora de treino e a senha que acabou de criar.
- **Conferir:**
  - Depois de salvar, a tela de entrada mostra *Senha nova criada. Entre com ela.*
  - O **Início** do prescritor abre com *Olá, Prescritora* e os números do dia: **Pacientes ativos**, **Consultas hoje**, **Pedidos esperando**, **Escalas vencidas** e **Avisos não lidos**.
  - Mostre o menu: **Início**, **Pacientes**, **Progresso**, **Indicadores**, **Agenda**, **Horários de atendimento**, **Avisos** e **Perfil**.
  - Volte à janela da administração e atualize: o botão **Corrigir e-mail** sumiu, porque a senha já foi criada.
- **Se der errado:**
  - *Este link não vale mais*: ele venceu ou já foi usado. A administração gera outro em **Senha nova**.
  - A tela diz *Você está conectado como* com o nome de outra pessoa: aquela janela já tinha alguém logado. Clique em **Sair e continuar** e a tela **Criar senha nova** segue com o mesmo link.

A partir daqui, deixe a prescritora de treino logada na **janela normal** (a administração sai) e use a janela anônima para os pacientes.

## 4. horários de atendimento e dias sem atendimento (7 min)

Janela normal, prescritora de treino.

- **Mostrar:** sem horários, o paciente não consegue pedir consulta. Abra **Agenda** e mostre o aviso *Você ainda não cadastrou horários de atendimento*.
- **Onde clicar:**
  1. **Cadastrar horários** no aviso, ou **Horários de atendimento** no menu.
  2. Em **Duração das consultas**, escolha **Primeira consulta** e **Retorno** (por exemplo, 90 e 60 minutos).
  3. Em **Períodos da semana**, clique em **Adicionar período** em cada dia de atendimento e ajuste **Das** e **às**. Um dia pode ter mais de um período, como manhã e tarde.
  4. **Salvar horários**.
  5. Em **Dias sem atendimento**, escolha um **Dia** da semana que vem e clique em **Marcar folga**.
- **Conferir:**
  - Aparece *Horários salvos. Seus pacientes já podem pedir consulta nesses períodos.*
  - Quem ainda não foi atendido vê horários do tamanho da primeira consulta; os outros, do tamanho do retorno.
  - A folga aparece na lista. Abra a **Agenda**, que começa na semana de hoje, e avance para a semana que vem com a seta **Próxima semana**, ao lado de **Esta semana**: o dia da folga ganha a etiqueta **sem atendimento**. Os pacientes não veem horários nesse dia.
  - A folga não cancela nada: se já houver consulta ou pedido no dia, a tela avisa quantos são, para remarcar ou cancelar na **Agenda**.
- **Se der errado:**
  - *Em cada período, o horário de fim precisa ser depois do início*: confira os horários do período.
  - Folga marcada no dia errado: clique na lixeira ao lado do dia (*Voltar a atender nesse dia*).
  - Salvar sem nenhum período fecha a agenda para os pacientes. A tela avisa quando isso acontece.

## 5. convite para o paciente (4 min)

Janela normal, prescritora de treino.

- **Mostrar:** o paciente só cria conta pelo link que o prescritor gera. Cada link serve para um cadastro e vale 7 dias.
- **Onde clicar:**
  1. **Convidar paciente** (no **Início** ou em **Pacientes**).
  2. **Gerar link de convite**.
  3. **Copiar link** e cole num bloco de notas. Este é o do paciente adulto.
  4. **Gerar outro link para outro paciente**, copie e cole também. Este é o do paciente menor.
  5. **Gerar outro link para outro paciente** mais uma vez. Este terceiro faz de conta que foi para a pessoa errada: em **Convites em aberto**, clique em **Cancelar** na primeira linha, que é a do convite mais novo.
- **Conferir:**
  - Embaixo do link aparece *Vale até* com a data de daqui a 7 dias.
  - Antes de cancelar, os três links aparecem em **Convites em aberto**, cada um com *Gerado em* e o botão **Cancelar**.
  - Depois de cancelar, o terceiro sai da lista e da tela, e na lista ficam só os dois guardados no bloco de notas. Quem abrir o link cancelado vê *Este convite não vale mais*.
  - **Enviar pelo WhatsApp** abre o WhatsApp com a mensagem pronta. Na capacitação, não envie para ninguém.
- **Se der errado:** cancelou um dos convites guardados: ele não volta. Gere outro em **Gerar link de convite** (ou **Gerar outro link para outro paciente**, se um link ainda estiver na tela) e use o novo no lugar dele. Link que já virou conta não aparece mais em **Convites em aberto** e não pode ser cancelado.

## 6. cadastro do paciente adulto e do menor (12 min)

Janela anônima, sem ninguém logado.

### paciente adulto

- **Mostrar:** o paciente preenchendo o próprio cadastro e aceitando o termo.
- **Onde clicar:**
  1. Cole o primeiro link de convite. Aparece *Convite de Prescritora Treino* e a tela **Criar conta**.
  2. Em **Seus dados**, preencha **Nome completo**, **Nome social (opcional)** com "Sam Treino", **CPF**, **Data de nascimento** e **Telefone com DDD**.
  3. Preencha **Endereço**.
  4. **Familiar ou cuidador (opcional)** pode ficar em branco ou receber um contato de quem ajuda no tratamento.
  5. Em **Acesso**, preencha **E-mail**, **Senha** e **Repita a senha**.
  6. Clique em **termo de consentimento**, mostre o texto e clique em **Li e aceito**.
  7. Mostre a caixa **Aceito participar da pesquisa da clínica**: é opcional, não muda o atendimento e pode ser retirada depois no **Perfil**.
  8. **Criar conta**.
- **Conferir:**
  - O paciente cai direto no **Início** dele, chamado pelo nome social.
  - Na janela do prescritor, em **Pacientes**, o paciente aparece pelo nome social. Em **Abrir histórico**, o cabeçalho mostra **Nome civil** ao lado, porque o prontuário identifica a pessoa.
  - Em **Convidar paciente**, o link usado saiu de **Convites em aberto**.
  - Os textos dos dois termos ainda são rascunhos à espera de revisão: mostre onde ficam, sem discutir o conteúdo como definitivo.

### paciente menor de idade

- **Mostrar:** com menos de 18 anos, o responsável legal entra no cadastro e no aceite.
- **Onde clicar:**
  1. Clique em **Sair** na janela anônima e cole o segundo link de convite.
  2. Preencha **Data de nascimento** com a data de uns 12 anos atrás. A seção vira **Dados do paciente** e a de contato vira **Responsável legal**.
  3. Preencha **Nome** e **Parentesco** do responsável (são obrigatórios). **Telefone com DDD** é opcional.
  4. Mostre o aceite, que agora diz *Sou o responsável legal pelo paciente e aceito, em nome dele, o termo de consentimento*. No texto do termo, o botão é **Li e aceito como responsável**.
  5. Antes de criar a conta, mude a data para uns 17 anos atrás e mostre a diferença: com 16 ou 17 anos, o paciente e o responsável aceitam juntos (*Eu, paciente, e meu responsável legal lemos e aceitamos*), com o botão **Lemos e aceitamos**. Volte para a data de 12 anos.
  6. Marque o aceite e clique em **Criar conta**.
- **Conferir:**
  - A caixa da pesquisa não aparece para menor de 18 anos.
  - Ao trocar de faixa de idade, o aceite desmarca sozinho, para a pessoa certa marcar o texto certo.
  - O nome e o parentesco do responsável ficam guardados junto com o aceite. No histórico do paciente, na janela do prescritor, aparecem como **Familiar ou cuidador**.
- **Se der errado:**
  - *Este convite não vale mais*: o link venceu, foi cancelado ou já foi usado. Gere outro.
  - *Não foi possível criar a conta com esses dados. Confira o e-mail e o CPF*: o e-mail ou o CPF já tem conta. Use outros.
  - *CPF inválido. Confira os números*: use um CPF fictício com dígitos válidos.
  - A tela diz *Você está conectado como* com o nome de outro paciente: a janela tinha alguém logado. Clique em **Sair e continuar o cadastro** e a tela **Criar conta** segue com o mesmo convite.

## 7. consulta, prescrição e agenda (12 min)

Janela normal, prescritora de treino. Na janela anônima, saia do paciente menor e entre como o paciente adulto.

### registrar a consulta

- **Mostrar:** o registro de uma consulta que acabou de acontecer.
- **Onde clicar:**
  1. **Pacientes** e, na linha do paciente adulto, **Nova consulta**.
  2. Em **Quando e como**, confira **Data e hora** (vem com o momento atual e não aceita hora futura) e **Modalidade**.
  3. Preencha alguns campos com texto inventado: **Queixa principal e observações**, **Exame físico**, **Evolução do quadro**, **Hipótese diagnóstica**, **Conduta e plano terapêutico**. Pressão, peso e altura são opcionais e somem na consulta **Remota**.
  4. **Salvar consulta**.
- **Conferir:** aparece **Consulta registrada** com três caminhos: **Emitir prescrição**, **Aplicar mini-exame** e **Ver histórico do paciente**.
- **Se der errado:**
  - *Preencha pelo menos um campo clínico da consulta*: escreva algo em pelo menos um dos campos de texto.
  - *A consulta registrada precisa já ter acontecido*: esta tela é para o atendimento que já aconteceu. Consulta futura se marca na **Agenda**.

### emitir a prescrição

- **Onde clicar:**
  1. **Emitir prescrição**.
  2. Em **Produto**, preencha **Produto** e escolha o **Espectro**. Marca, lote e volume são opcionais.
  3. Em **Composição do óleo**, preencha **Canabinoide**, **Concentração** e **Unidade**. **Adicionar canabinoide** cria outra linha.
  4. Em **Posologia**, escreva um texto de exemplo, como o que aparece em cinza no próprio campo. **Adicionar semana** monta um escalonamento de dose.
  5. Em **Acompanhamento**, preencha **Duração do tratamento em dias** e, se quiser, **Próxima consulta**.
  6. **Emitir prescrição**.
- **Conferir:**
  - *A prescrição agora é a vigente do paciente.*
  - Na janela do paciente, o **Início** mostra **Prescrição de agora**, e o menu **Prescrições** mostra a mesma receita em **Vigente**.
  - No histórico do paciente, a consulta tem os botões **Editar registro**, **Emitir prescrição**, **Mini-exame** e **Anular**. Nada é apagado: o registro feito por engano é anulado com o motivo e continua no histórico.

### pedido de consulta pela agenda

- **Onde clicar:**
  1. Na janela do paciente, **Consultas**: **Escolha o dia**, **Escolha o horário** e, em **Confirme o pedido**, a **Modalidade** e o **Motivo da consulta (opcional)**. Depois, **Pedir consulta**.
  2. Na janela do prescritor, **Agenda**: o pedido está em **Pedidos aguardando resposta**. Clique em **Confirmar**.
- **Conferir:**
  - Como o paciente já tem uma consulta registrada, o pedido sai do tamanho do **Retorno**, e a agenda mostra *Retorno · Presencial* (ou *Remota*).
  - O dia de folga do passo 4 não oferece horários.
  - O paciente recebe o aviso da confirmação em **Avisos**, e o pedido dele muda de **Aguardando resposta** para **Agendada**.
- **Se der errado:**
  - Não aparece horário para o paciente: confira se há período cadastrado e se os próximos 30 dias não caíram todos em folga.
  - *Este pedido de consulta ainda não foi confirmado na agenda*: só consulta confirmada recebe registro clínico e prescrição.
  - Consulta marcada que ainda não começou não mostra **Registrar atendimento**. O botão aparece quando chega a hora; antes disso, só **Remarcar** e **Cancelar**.

> **Pacientes do piloto que já eram da clínica.** O sistema chama de primeira consulta toda consulta de quem ainda não tem nenhuma consulta concluída nele. Por isso, quem já era paciente antes do 2AG aparece como **Primeira consulta**, com a duração dela, até que:
>
> - o prescritor registre uma consulta passada, pelo histórico do paciente em **Nova consulta**, com a data do último atendimento; ou
> - o prescritor corrija o tipo quando confirmar o pedido: depois de **Confirmar**, clique em **Remarcar**, mantenha o dia e o horário e escolha **Tipo: Retorno** (a **Duração** muda junto). O paciente recebe o aviso de consulta remarcada. Ao marcar direto pela **Agenda**, em **Nova consulta**, o tipo também pode ser escolhido.
>
> Falta e cancelamento não contam como consulta concluída.

## 8. escalas e acompanhamento automático de 90 dias (8 min)

Janela normal, prescritora de treino, no histórico do paciente adulto.

### envio avulso

- **Onde clicar:**
  1. **Pacientes**, **Abrir histórico** e **Enviar escalas**.
  2. Marque **Avaliação inicial** e **Escala de ansiedade de Hamilton**. A busca **Buscar escala pelo nome** ajuda quando a lista cresce.
  3. **Enviar ao paciente**.
- **Conferir:**
  - O histórico volta com o aviso de quantas escalas foram enviadas.
  - Em **Escalas**, as duas aparecem em **Aguardando o paciente**, com prazo de 7 dias.
  - O **Mini-Exame do Estado Mental (MEEM)** não aparece nesta lista: ele é aplicado pelo prescritor na consulta, pelo botão **Mini-exame**.

### acompanhamento automático

- **Onde clicar:**
  1. No histórico, **Acompanhamento automático**.
  2. Em **Período**, deixe **Começa em** com a data de hoje e **Duração em dias** com 90.
  3. Em **Escalas e frequência**, marque **Acompanhamento semanal** com **Toda semana**. Se quiser, marque também **Diário do sono** com **A cada 14 dias**: aparece **Programação do diário do sono**, com **Ir dormir às** e **Levantar às**.
  4. **Iniciar acompanhamento**.
- **Conferir:**
  - *Acompanhamento iniciado. O sistema vai enviar as escalas sozinho.*
  - O quadro **Em andamento** mostra o selo **Ativo**, o período de 90 dias e a tabela **Escala** e **Frequência**.
  - Começando hoje, a primeira rodada sai na hora: o paciente já vê o **Acompanhamento semanal** para responder. As rodadas seguintes saem sozinhas no servidor de verdade.
  - A **Avaliação inicial** não entra no acompanhamento: ela é da triagem e é enviada uma vez só, pelo envio avulso.
- **Se der errado:**
  - *Este paciente já tem um acompanhamento em andamento*: só cabe um por paciente. **Encerrar acompanhamento** e crie outro.
  - *Escolha ao menos uma escala*: marque pelo menos uma.
  - Começando em outra data, a primeira rodada só sai pelas tarefas da manhã do dia de início, que não rodam na demonstração.

## 9. o paciente responde as escalas e a ficha diária (8 min)

Janela anônima, paciente adulto.

- **Mostrar:** o que o paciente vê e como ele responde.
- **Onde clicar:**
  1. No **Início**, o quadro **Esperando resposta** lista as escalas com *Responda até*. Clique em **Responder** na **Escala de ansiedade de Hamilton**.
  2. Responda todos os itens e clique em **Salvar respostas**.
  3. Volte e abra **Acompanhamento semanal**. Preencha o dia de hoje e **Salvar respostas**.
  4. Abra **Escalas** no menu: **Minhas avaliações clínicas** mostra **Esperando resposta** e **Respondidas**.
  5. Se der tempo, mostre a **Avaliação inicial**: é a ficha de triagem, mais longa, e termina em **Enviar anamnese**.
- **Conferir:**
  - A Hamilton mostra *Respostas salvas. Resultado:* com o escore e a faixa.
  - Na ficha diária, o dia passa de **em branco** para **preenchido**. O que o paciente não responder fica em branco, e em branco não vale zero.
  - Em **Respondidas**, o paciente tem **Ver e corrigir** enquanto o prescritor não analisa a resposta.
  - No celular, **Escalas** não fica na barra de baixo: o paciente chega pelo **Início**, no quadro **Esperando resposta**.

### os dias que o paciente esqueceu

- **Onde clicar:** saia e entre como `paciente@email.com` (Maria Souza). Em **Escalas**, clique em **Responder** no **Acompanhamento semanal**.
- **Conferir:**
  - A grade **Dias do período** mostra todos os dias desde o início do período até hoje, cada um como **preenchido** ou **em branco**, e o aviso *Esqueceu algum dia? Escolha o dia na grade e preencha agora.*
  - O paciente escolhe um dia em branco, preenche e salva, e repete para os outros dias esquecidos, tudo de uma vez.
  - Dia que ainda não chegou nunca aparece na grade.
  - Na demonstração que subiu hoje, os dois primeiros dias do período da Maria já vêm preenchidos e só o de hoje está em branco. Mostre que qualquer dia da grade abre do mesmo jeito: o dia em branco de dias atrás seria preenchido como o de hoje.

### do lado do prescritor

- **Conferir:** na janela do prescritor, no histórico do paciente adulto, a Hamilton está em **Respondidas** com **Ver respostas**, **Marcar como analisada** e **Anular**. Clique em **Marcar como analisada**: a partir daí o paciente não corrige mais aquela resposta. Em **Avisos**, o prescritor recebeu o aviso da escala respondida. Nas fichas diárias, o aviso chega uma vez só, quando o período fecha.
- **Se der errado:**
  - *Falta preencher*: a escala com escore só salva com todos os itens respondidos.
  - *O prescritor já analisou esta resposta. Para corrigir, fale com ele*: o prescritor clica em **Anular**, escreve o **Motivo da anulação** e confirma em **Anular registro**. A escala volta para **Esperando resposta** do paciente e, em **Responder**, abre em branco com o aviso *A resposta desta data foi anulada pelo prescritor e continua no histórico. Você pode responder de novo.* O paciente responde outra vez, mesmo que seja no mesmo dia.

## 10. gráficos de progresso e comentários (6 min)

Janela normal. Saia da prescritora de treino e entre como `prescritor@email.com` (Ana Lima), que tem dois meses de dados.

- **Mostrar:** a evolução da Maria Souza ao longo do tratamento.
- **Onde clicar:**
  1. **Progresso** no menu e, em **Paciente**, escolha Maria Souza.
  2. **Período**: **Últimos 60 dias**. **Escala**: **Acompanhamento semanal**. **O que acompanhar**: **Dor**.
  3. **Marcar as consultas** já vem marcado. Marque também **Mostrar as gotas do dia**.
  4. Troque **Escala** para **Escala de ansiedade de Hamilton**.
- **Conferir:**
  - Cada ponto é um preenchimento. Dia sem resposta não aparece no gráfico, em vez de aparecer como zero.
  - As consultas viram linhas verticais, escritas *consulta* ou *consulta + receita*, e o quadro **Consultas no período** lista as datas.
  - Na Hamilton aparecem as linhas das faixas de interpretação.
  - O quadro **O que o paciente escreveu** junta os comentários das escalas no período, com a data e o nome da escala.
  - O mesmo gráfico abre pelo histórico do paciente, no botão **Progresso**. O paciente vê o dele no menu **Progresso**.
- **Se der errado:** *Nenhum registro preenchido neste período*: aumente o **Período** ou troque a escala.

## 11. exportação (5 min)

Janela normal, Ana Lima.

- **Mostrar:** o arquivo com identificação e o modo sem identificação, que é o da pesquisa.
- **Onde clicar:**
  1. **Pacientes**, **Abrir histórico** de João Pedro Almeida e **Exportar**.
  2. Na janela **Exportar dados**, clique em **Baixar CSV** em **Consultas**.
  3. Marque **Modo sem identificação, para pesquisa** e baixe o mesmo arquivo de novo. Compare os dois.
  4. Feche e abra **Exportar** no histórico da Maria Souza.
  5. Mostre **Versão para impressão** e **Imprimir ou salvar em PDF**.
- **Conferir:**
  - Os arquivos são **Consultas**, **Prescrições**, **Escalas respondidas** e **Anamnese**. Cada um abre direto numa planilha.
  - No modo sem identificação, nome, CPF, e-mail, telefone e endereço ficam de fora, e paciente e prescritor aparecem só por um número.
  - **Esse modo não é anônimo.** A clínica sabe de quem é cada número, as datas saem como estão e o texto livre, como queixa e observação, sai como foi escrito e pode citar nomes. Pela LGPD o arquivo continua sendo dado de saúde: guarde e envie com o mesmo cuidado do prontuário.
  - Na Maria Souza a caixa fica desligada: ela não aceitou participar da pesquisa, então o arquivo sem identificação não sai. O paciente pode aceitar no **Perfil** dele.
  - A série de um gráfico sai na tela **Progresso**, em **Baixar esta série em CSV**, com a caixa **Baixar o CSV sem identificação, para pesquisa**.
- **Se der errado:** a caixa do modo sem identificação não marca: o paciente não aceitou a pesquisa. Isso é o esperado.

Ao fim da sessão, apague os arquivos baixados. Mesmo com dados inventados, arquivo exportado não vai para pasta compartilhada nem para o repositório.

## 12. trilha de auditoria (4 min)

- **Onde clicar (prescritor):** no histórico da Maria Souza, **Histórico de acesso**. Use **De**, **Até** e **Filtrar** para mudar o período; **Mais recentes** e **Mais antigos** mudam de página.
- **Onde clicar (administração):** saia e entre como `admin@email.com`. Menu **Auditoria**.
- **Conferir:**
  - Cada linha diz quando, o que foi feito, em qual registro e quem fez (com o perfil: paciente, prescritor ou administrador).
  - A abertura do histórico que acabou de acontecer já está na trilha: abrir o prontuário também fica registrado, não só criar e alterar.
  - Na **Auditoria** da administração, o paciente aparece só como *paciente nº*, sem nome e sem conteúdo clínico. A criação da conta da prescritora de treino, do passo 2, também está lá.
- **Se der errado:** *Nenhum registro nesse período*: a trilha abre nos últimos 30 dias. Amplie o período em **De**.

## 13. arquivar paciente (3 min)

Janela normal: saia da administração e entre de novo como a prescritora de treino.

- **Mostrar:** o que acontece com quem teve alta ou parou o tratamento.
- **Onde clicar:**
  1. **Pacientes** e, na linha do paciente adulto, **Arquivar**.
  2. Leia a mensagem da janela **Arquivar paciente** e confirme em **Arquivar**.
  3. Abra a aba **Arquivados**.
- **Conferir:**
  - *O prontuário continua guardado na aba Arquivados.*
  - O acompanhamento automático foi encerrado e as escalas em aberto foram fechadas. O histórico do paciente mostra *No arquivo desde* com a data.
  - O paciente continua entrando no sistema e vendo o próprio histórico. Escala enviada à mão depois do arquivamento continua chegando para ele.
  - **Reativar** devolve o paciente para **Ativos**, mas não recria o acompanhamento automático: monte um novo, se precisar.
  - Diferença importante: **arquivar** é do prescritor e mexe na lista e no acompanhamento. **Desativar** a conta é da administração, em **Pacientes** no menu dela, e tira o acesso da pessoa na hora. Nos dois casos nada é apagado.
- **Se der errado:** arquivou o paciente errado: **Arquivados** e **Reativar**.

## 14. perguntas e checklist (5 min)

Abra para perguntas e passe o checklist abaixo com a equipe da clínica. Mostre também o [guia do paciente](./guia-do-paciente.md), uma página que a clínica pode imprimir ou mandar junto com o link de convite (antes, preencha as linhas em branco do começo dele: o endereço do 2AG e o contato e o horário da clínica).

---

## checklist final

- [ ] A administração cria a conta do prescritor e entrega o link de primeiro acesso.
- [ ] O prescritor cria a própria senha e entra.
- [ ] Os horários de atendimento têm a duração da primeira consulta e do retorno, e os dias de folga estão marcados.
- [ ] O prescritor gera, copia e cancela um convite.
- [ ] O cadastro funciona para adulto e para menor com o responsável legal.
- [ ] O prescritor registra a consulta e emite a prescrição, e o paciente vê a prescrição vigente.
- [ ] O prescritor confirma um pedido e sabe corrigir o tipo dos pacientes do piloto.
- [ ] O prescritor envia escalas e inicia o acompanhamento de 90 dias.
- [ ] O paciente responde as escalas e preenche os dias esquecidos da ficha diária.
- [ ] O prescritor lê o gráfico, as consultas marcadas e os comentários.
- [ ] Todos sabem que o modo sem identificação não é anônimo.
- [ ] O prescritor e a administração sabem onde fica a trilha de auditoria.
- [ ] O prescritor sabe arquivar e reativar, e a administração sabe a diferença para desativar.
- [ ] Os arquivos baixados na sessão foram apagados.
