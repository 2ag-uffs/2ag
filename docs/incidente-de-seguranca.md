# incidente de segurança com dados pessoais

**Rascunho para a clínica validar.** Este texto foi escrito pela equipe do 2ag a partir do que o sistema guarda e do regulamento da ANPD, e ainda não foi lido nem aprovado pela clínica. Quem responde pelos dados é a clínica, que é a controladora (LGPD): é ela, com o encarregado de dados, que decide se um incidente é comunicado e é ela que comunica. Os nomes e os contatos abaixo estão em branco de propósito: o repositório é público, então a versão preenchida fica com a clínica, fora dele.

Ninguém escreve um roteiro no meio de um incidente. A ideia é ter este pronto antes: o que fazer nos primeiros 3 dias úteis, o modelo do registro de cada incidente e o modelo do aviso às pessoas.

---

## quem faz o quê

| papel | quem é | contato |
| :--- | :--- | :--- |
| encarregado de dados da clínica | `[nome]` | `[e-mail e telefone]` |
| direção da clínica, que decide pela controladora | `[nome]` | `[e-mail e telefone]` |
| responsável técnico pelo servidor | `[nome]` | `[e-mail e telefone]` |
| equipe do 2ag, que ajuda a entender o que aconteceu no sistema | `[nome]` | `[e-mail e telefone]` |

Quem perceber o problema avisa o encarregado na hora, por telefone se preciso. Não espere ter certeza.

---

## o que conta como incidente

Qualquer acontecimento que deixe um dado pessoal ser visto, copiado, alterado ou perdido por quem não devia, por acidente ou de propósito. No 2ag, por exemplo:

- alguém entrou na conta de um prescritor, de um paciente ou da administração sem ser o dono dela;
- o servidor foi invadido, ou alguém de fora leu o banco, o `.env` ou os logs;
- um arquivo de backup saiu do controle da clínica, ou a chave privada do backup vazou ou se perdeu;
- um CSV ou um PDF baixado do sistema foi para a pessoa errada, ou o computador ou o pendrive onde ele estava foi perdido ou roubado;
- um e-mail do sistema foi para o endereço errado;
- um paciente ou um prescritor viu dado de quem não é dele;
- dados foram apagados ou estragados e o backup não trouxe de volta.

O sistema guarda dado de saúde, que a LGPD trata como sensível, e pode guardar dado de criança, de adolescente e de idoso. Pelo regulamento da ANPD (Resolução CD/ANPD nº 15/2024, art. 5º), são justamente os casos em que um incidente que afete de forma significativa os direitos das pessoas precisa ser comunicado. Quem faz essa avaliação é o encarregado.

---

## o prazo

A comunicação à ANPD e o aviso às pessoas atingidas têm **3 dias úteis**, contados de quando a clínica soube que o incidente atingiu dados pessoais (LGPD, art. 48, e Resolução CD/ANPD nº 15/2024, arts. 6º e 9º). Por isso a primeira coisa a anotar é o dia e a hora em que a clínica soube.

---

## os primeiros 3 dias úteis

### na hora

- [ ] Anotar o dia e a hora em que a clínica soube, quem avisou e o que foi visto. O prazo conta daqui.
- [ ] Avisar o encarregado e a direção.
- [ ] Conter, sem apagar rastro:
  - conta usada por outra pessoa: a administração desativa a conta em *Prescritores* ou em *Pacientes*, o que tira o acesso na requisição seguinte. Desativar cancela na hora os links de senha nova e os pedidos de troca de e-mail que estavam pendentes na conta, e eles não voltam a valer na reativação. Reativada para o dono, a conta também não traz de volta as sessões de antes, e ele cria outra senha em *Esqueci minha senha* (o prescritor que ainda não tinha criado a senha recebe outro link pela administração, em **Senha nova**). Confira no perfil dele se o e-mail de acesso continua o certo: a troca de e-mail já confirmada antes da desativação não é desfeita;
  - senha da administração: trocar em *Perfil*, o que encerra as outras sessões dela;
  - servidor ou `.env` exposto: trocar o `JWT_SECRET`, o que encerra as sessões de todo mundo, e os outros segredos do `.env` (seção 4 do [`guia-de-deploy.md`](./guia-de-deploy.md)), com o responsável técnico. A senha do banco precisa mudar dentro do postgres e no `.env` juntos, e a do e-mail, no provedor e no `.env`;
  - chave privada do backup exposta: gerar um par novo, como em [`backup-e-restauracao.md`](./backup-e-restauracao.md). Os arquivos antigos continuam abrindo com a chave antiga, então conte como expostos os que saíram do controle da clínica.
- [ ] Guardar as evidências antes de consertar. Não reinstale o servidor nem apague log:
  - os logs dos containers, que o docker descarta quando passam de 50 MB: `docker compose logs --no-color api > api.log`, e o mesmo para `web` e `banco`;
  - um backup do banco como ele está: `./scripts/backup-banco.sh /var/backups/2ag`;
  - a trilha de auditoria do período: *Auditoria*, na conta da administração, e *Histórico de acesso* de cada paciente atingido, na conta do prescritor dele.

### 1º dia útil: entender o tamanho

- [ ] Quais dados foram atingidos: identificação e contato, dados de saúde (anamnese, escalas, consultas, prescrições), senha ou sessão.
- [ ] De quantas pessoas, e quantas delas são crianças, adolescentes ou idosos.
- [ ] Desde quando, por onde, e se ainda está acontecendo. A trilha de auditoria diz quem abriu, alterou ou exportou cada prontuário; o log do `web` tem o endereço de rede e a tela de cada acesso.
- [ ] O que protegia esses dados antes do incidente (senha com hash, https, backup cifrado, acesso só do prescritor do paciente).

### 2º dia útil: decidir e preparar

- [ ] O encarregado avalia o risco e o dano possível para as pessoas, e a direção decide se comunica. A decisão e o motivo entram no registro, **também quando a decisão é não comunicar**.
- [ ] Preparar a comunicação à ANPD, pelo canal de comunicação de incidentes que estiver no site da ANPD. Ela pede, entre outras coisas, o que este roteiro já levantou: o que aconteceu e a causa, quais dados, quantas pessoas (e quantas crianças, adolescentes e idosos), os riscos, as medidas de antes e de depois, as datas do incidente e de quando a clínica soube, e os dados do encarregado.
- [ ] Preparar o aviso às pessoas, pelo modelo do fim desta página.

### até o fim do 3º dia útil: comunicar

- [ ] Enviar a comunicação à ANPD. Se ainda faltar informação, envie o que já se sabe dentro do prazo: o regulamento deixa completar depois, com justificativa, em até 20 dias úteis (art. 6º, § 3º).
- [ ] Avisar cada pessoa atingida, de forma direta e individual sempre que der (telefone, e-mail, mensagem ou carta, o canal que a clínica já usa com ela), em linguagem simples. Para paciente com menos de 18 anos, a clínica combina com o encarregado como avisar o responsável legal.
- [ ] Guardar cópia do que foi enviado e de por qual meio. A clínica junta ao processo na ANPD a declaração de que avisou as pessoas (art. 9º, § 4º).
- [ ] Preencher o registro do incidente.

### depois

- [ ] Corrigir a causa e anotar no registro o que mudou.
- [ ] Rever este roteiro com o que não funcionou.

---

## registro de incidentes

Todo incidente ganha um registro, **inclusive o que não foi comunicado** à ANPD nem às pessoas. O regulamento pede que o registro fique guardado por no mínimo 5 anos, contados da data do registro (art. 10). Ele fica com a clínica, fora do sistema e fora do repositório, com acesso restrito. Não coloque nome de paciente no registro: a lista de quem foi atingido fica à parte.

Modelo, um por incidente:

| campo | preencher |
| :--- | :--- |
| número do registro | |
| quem preencheu, e quando | `[papel]` |
| quando aconteceu, se der para saber | |
| quando a clínica soube | |
| o que aconteceu e como foi descoberto | |
| a causa, se foi encontrada | |
| quais dados foram atingidos (identificação, contato, saúde, senha) | |
| quantas pessoas, e quantas crianças, adolescentes e idosos | |
| o risco e os danos possíveis para essas pessoas | |
| o que foi feito para conter e para corrigir | |
| foi comunicado à ANPD? quando, por qual meio e com que conteúdo | |
| as pessoas foram avisadas? quando, por qual meio e com que texto | |
| se não foi comunicado, por quê | |
| o que muda para não acontecer de novo | |

---

## modelo de aviso às pessoas

O aviso é da clínica e sai pelos canais dela, não pelo sistema. O regulamento pede linguagem simples e estes pontos: quais dados, o que protegia esses dados, os riscos, o que foi e o que será feito, quando a clínica soube, o motivo da demora se passou dos 3 dias úteis e um contato (art. 9º). Troque o que está entre colchetes e tire o que não se aplica.

> **Assunto:** Aviso sobre os seus dados
>
> Olá, `[nome]`.
>
> No dia `[data em que a clínica soube]`, a `[nome da clínica]` soube de um problema de segurança que atingiu dados seus guardados no sistema que usamos para acompanhar o seu atendimento.
>
> **O que aconteceu:** `[uma ou duas frases simples, sem termo técnico]`.
>
> **Quais dados:** `[por exemplo: nome, telefone e e-mail; respostas de formulários; registros de consulta]`.
>
> **O que isso pode causar:** `[os riscos, em palavras simples]`.
>
> **Como os dados estavam protegidos:** `[por exemplo: acesso só com senha, conexão cifrada]`.
>
> **O que já fizemos e o que ainda vamos fazer:** `[as medidas]`.
>
> **O que você pode fazer:** `[por exemplo: criar uma senha nova no sistema; desconfiar de mensagem que peça os seus dados em nosso nome]`.
>
> `[Se o aviso sair depois de 3 dias úteis: por que demorou.]`
>
> Para tirar dúvidas, fale com `[nome do encarregado de dados]`, pelo `[e-mail e telefone]`.
>
> `[nome da clínica]`

Como os outros e-mails, o assunto não diz nada do tratamento: ele aparece na tela bloqueada do celular.

---

## o que falta a clínica decidir

- [ ] Quem é o encarregado de dados e quem responde na ausência dele.
- [ ] Onde fica o registro de incidentes e quem pode ler.
- [ ] Se a equipe do 2ag é chamada em todo incidente ou só nos que envolvem o servidor.
- [ ] Revisar este texto com quem cuida da parte jurídica da clínica e tirar a marca de rascunho.
