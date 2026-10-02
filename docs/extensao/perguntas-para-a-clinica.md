# perguntas para a clínica

Dúvidas que apareceram durante o desenvolvimento e que **não dá para responder do lado técnico** — dependem de como a prescritora trabalha de fato. Cada uma trava ou enviesa uma decisão de modelagem.

Serve também como registro de diário de bordo: o que foi perguntado, quando e o que ela respondeu.

---

## 1. Composição do óleo prescrito

**Status:** parcialmente respondida · **trava:** modelagem da prescrição (RN03)

A tela de prescrição oferece cinco opções fixas de formulação: `CBD Isolado`, `CBD Broad Spectrum`, `CBD Full Spectrum`, `THC Isolado`, `THC + CBD`. E aceita **um único** número de concentração.

Há indício de que isso não corresponde à prática: a prescrição parece envolver **percentuais de cada canabinoide, definidos caso a caso**, porque a resposta varia por pessoa e por condição tratada.

Se for isso, a lista fixa impede o registro correto — "CBD Full Spectrum" não diz se é 10% ou 20%, nem quanto de THC tem dentro.

**Já respondido (01/10/2026):** no sistema que ela usa hoje, cada produto é cadastrado com marca, concentração em mg/mL e volume do frasco. Ela ofereceu mandar um print dessa tela. O sistema já guarda volume e lote; o que falta saber é se ela usa o lote.

**Perguntar:**

- Quando você prescreve, você escolhe o **canabinoide** e o **espectro** separadamente, ou pensa nas combinações já prontas?
- O mg/mL é de cada canabinoide (ex.: CBD 20 mg/mL, THC 1 mg/mL), ou é uma concentração só do produto?
- Existe lote do produto que você precise anotar? *(a tabela de necessidades de 2025 menciona lote)*

**Por que importa:** hoje só cabe um número. Se forem dois ou três canabinoides com percentuais próprios, a prescrição precisa de uma lista, como já foi feito com o escalonamento de dose.

---

## 2. Sinais vitais na consulta

**Status:** respondida em 01/10/2026 · **afeta:** modelo da consulta (RF04)

A tela de consulta já coletava pressão arterial, peso e altura, mas o modelo não tinha onde guardar e esses dados se perdiam. Foram acrescentados, com peso e altura como número para permitir acompanhar ao longo do tratamento.

A clínica não mede sinais vitais: a maioria das consultas é online. Os campos continuam no modelo, opcionais, para a consulta presencial. Resposta na tabela do fim.

---

## 3. Ficha de acompanhamento semanal

**Status:** aberta · **afeta:** RF20

A ficha em papel (`docs/scales/acompanhamento-semanal.pdf`) é uma **grade semanal**: um formulário cobre um período, com uma coluna por dia e o registro de gotas da manhã e da tarde em cada uma.

**Perguntar:**

- O paciente preenche todo dia, ou senta no fim da semana e preenche os sete de uma vez?
- Se ele pular um dia, tudo bem deixar em branco? *(hoje o sistema registra como ausente, não como zero — item não respondido não vale "sem dor")*

---

## 4. Piloto: quais pacientes e quais dados

**Status:** parcialmente respondida · **afeta:** o que dá para mostrar em dezembro

Já respondido: **todas as oito escalas são essenciais**, nenhuma sai do piloto. Em 01/10/2026: ela vai convidar alguns pacientes mais próximos, e a importação de dados antigos é possível, mas ela ainda está organizando o histórico, que hoje está espalhado entre registros anteriores e o sistema que ela usa agora.

**Perguntar:**

- De qual dessas fontes viriam os dados antigos, em que formato e para quantos pacientes?
- Você consegue nos passar uma amostra **com os identificadores removidos**, para planejarmos a importação?
- Quantos pacientes entram no piloto? Os quatro que responderam o formulário em 2025 estão entre eles? Eles precisam concordar por escrito.

---

## 5. Consentimento e formalização

**Status:** parcialmente respondida · **trava:** o piloto não pode começar sem isso

**Já respondido (01/10/2026):** a clínica tem um termo próprio do tratamento, que ela ofereceu mandar (ver seção 9).

**Perguntar:**

- A clínica assina um termo de parceria com a universidade? *(a UFFS deve ter modelo)*
- O paciente aceita o consentimento dentro do sistema, ou continua assinando onde já assina hoje?
- Há algum dado que você **não** quer que saia do prontuário atual para o sistema novo?

---

## 6. Quem aplica a escala de Hamilton

**Status:** aberta · **afeta:** RF21 e RN09

No instrumento original a HAM-A é pontuada por quem entrevista, e o item 14 é "comportamento durante a entrevista", que só o avaliador observa. O sistema hoje entrega a escala para o paciente responder sozinho.

**Perguntar:**

- Você aplica a HAM-A em entrevista, pontuando cada item, ou o paciente responde sozinho?
- Se o paciente responde, como fica o item 14?

**Por que importa:** se a aplicação é em entrevista, a HAM-A passa para o lado do prescritor, como o MEEM, e deixa de virar tarefa do paciente.

Por enquanto a HAM-A continua com o paciente. Cada escala já registra quem a aplica, então mudar de lado é trocar uma linha.

---

## 7. Como a consulta é marcada

**Status:** parcialmente respondida · **afeta:** RF10 e RF11

A proposta é que o prescritor cadastre os horários disponíveis, o paciente solicite um horário e o prescritor confirme.

**Já respondido (01/10/2026):** ela nunca recusou um pedido, e usa pouco o autoagendamento do sistema atual. O fluxo de pedir e confirmar fica como está no piloto. O sistema já tem uma duração padrão por prescritor; falta saber se muda por tipo de consulta.

**Perguntar:**

- Quais dias e horários você atende? Isso muda de semana para semana?
- Quanto tempo dura cada tipo de consulta (primeira consulta, retorno)?

---

## 8. Lembrete de dose

**Status:** parcialmente respondida · **afeta:** RF34

**Já respondido (01/10/2026):** os ajustes de dose são combinados pelo WhatsApp, a partir das respostas do formulário, e isso varia de profissional para profissional. A pergunta sobre o lembrete em si ficou sem resposta.

**Perguntar:**

- Algum paciente precisa de lembrete para tomar a dose? Se sim, por qual canal?
- O ajuste de dose combinado pelo WhatsApp deve ficar registrado no sistema?

---

## 9. Termo de consentimento do sistema

**Status:** parcialmente respondida · **trava:** RF36 e o início do piloto

O cadastro já pede o aceite de um termo de consentimento e guarda a versão e a data de cada aceite. O texto atual é um **rascunho técnico** e precisa ser revisado e aprovado pela clínica, de preferência com apoio jurídico, antes de qualquer paciente real.

**Já respondido (01/10/2026):** a clínica tem um termo próprio do tratamento com cannabis (o paciente sabe que não há promessa de resultado, que os efeitos adversos foram explicados e que se compromete a ajustar a dose), e a prescritora ofereceu mandar o modelo. Ele não substitui o termo de dados do cadastro: os dois tratam de coisas diferentes. O uso dos dados em pesquisa ainda não acontece, mas vai acontecer (coortes prospectivas e evidência do mundo real), então o consentimento de pesquisa separado entra.

**Perguntar:**

- Qual a razão social e o CNPJ do instituto, que aparecem no termo como responsável pelos dados?
- Quem responde como encarregado de dados (LGPD, art. 41) e qual contato deve aparecer no termo?
- O(a) advogado(a) que você citou é do instituto ou da plataforma? Pode revisar o nosso termo de dados?
- A pesquisa já tem projeto aprovado no comitê de ética, com o texto do consentimento?

---

## 10. Ponto de corte do MEEM

**Status:** aberta · **afeta:** RF26 e RN14

O formulário do MEEM que a clínica aplica (`docs/scales/meem.pdf`) não traz faixa interpretativa nenhuma, e a RN14 manda mostrar o escore sempre com a interpretação. O sistema adotou o corte por escolaridade de Brucki et al. (2003): analfabeto 20 · 1 a 4 anos 25 · 5 a 8 anos 26,5 · 9 a 11 anos 28 · acima de 11 anos 29.

**Perguntar:**

- Você usa esse corte por escolaridade ou outro, como o de Bertolucci (1994)?
- A escolaridade do paciente é perguntada na consulta? Hoje ela é escolhida na hora do exame, porque é ela que define o corte.

**Por que importa:** o mesmo escore muda de interpretação conforme a tabela escolhida, e é a prescritora quem lê esse resultado.

---

## 11. Responsável ou cuidador

**Status:** aberta · **afeta:** cadastro do paciente e RF36

Muitos pacientes têm cuidador: idosos, filhos que acompanham os pais, crianças (resposta de 01/10/2026). O sistema hoje guarda um telefone só, o do próprio paciente, e o cadastro e o termo partem do princípio de que o titular é o paciente.

**Perguntar:**

- Basta nome, parentesco e telefone do responsável, ou precisa de e-mail também? Pode ter mais de um?
- O responsável preenche as escalas pelo login do paciente?
- O lembrete deve chegar também ao responsável?
- No caso de criança, quem aceita o termo?

---

## 12. Quem vê os gráficos de evolução

**Status:** aberta · **afeta:** RF27 e o item 3 do termo

Perguntada sobre o gráfico do MEEM, a resposta foi "os gráficos são só comigo, mas tem uma área do paciente que ainda precisa ser melhorada". Pelo contexto, parece descrever o sistema que ela usa hoje. Aqui o paciente vê a própria evolução em todas as escalas, inclusive o MEEM.

**Perguntar:**

- O paciente deve ver os próprios gráficos? Todos, alguns (por exemplo, sem o MEEM) ou nenhum?

---

## 13. Nome social

**Status:** aberta · **afeta:** RN15

O formulário do PSQI que a clínica aplica pede "nome do paciente (social)", e o sistema guarda um nome só. Em 01/10/2026 ela não soube dizer.

**Perguntar:**

- Algum paciente seu usa nome social? Se sim, ele deve aparecer no lugar do nome civil nas telas e nos relatórios?

---

## 14. Pesquisa do doutorado

**Status:** aberta · **afeta:** escopo

A prescritora prepara um estudo duplo-cego randomizado para o doutorado, com os dados codificados para manter o cegamento, e falou em "uma aba" só para isso. O sistema mostra a composição do óleo ao prescritor e ao paciente, não tem randomização e a exportação usa o id do banco. Fica fora do piloto.

**Perguntar:**

- Essa aba seria neste sistema ou no que você usa hoje?
- Para quando? Já existe protocolo aprovado no comitê de ética?
- A pesquisa de coorte começa junto com o piloto ou depois?

---

## 15. O que fica em cada sistema durante o piloto

**Status:** aberta · **afeta:** tudo o que seria registrado em dobro

A clínica já usa um sistema que emite a receita assinada, agenda, envia a ficha de acompanhamento pelo WhatsApp e guarda exames. Sem combinar o que passa para cá, a prescritora digita a mesma coisa duas vezes.

**Perguntar:**

- Durante o piloto, o que você faz neste sistema e o que continua no de hoje: receita, agenda, escalas, ficha de acompanhamento?
- Se lançar uma prescrição no paciente errado, como você corrige hoje?
- Quais são as "várias outras" escalas que você usa? Alguma precisa entrar no piloto?

---

## respondidas

| Quando | Pergunta | Resposta |
| :--- | :--- | :--- |
| set/2026 | Quais escalas entram no piloto? | Todas as oito. Nenhuma é dispensável |
| 01/10/2026 | Você mede pressão, peso e altura em toda consulta? | Não mede sinais vitais: a maioria das consultas é online. Os campos ficam opcionais |
| 01/10/2026 | A clínica já usa um termo de consentimento? | Sim, um termo do tratamento com cannabis. Ela ofereceu mandar o modelo. Não substitui o termo de dados |
| 01/10/2026 | Os dados poderão ser usados em pesquisa? | Ainda não, mas vão ser (coortes prospectivas, evidência do mundo real). Entra o consentimento de pesquisa separado |
| 01/10/2026 | O motivo da recusa de um pedido de consulta vai para o prontuário? | Ela nunca recusou um pedido no sistema atual, onde o paciente quase não se autoagenda. O motivo continua só no aviso |
| 01/10/2026 | Pode apagar um paciente? *(comentário dela)* | Não. Aqui também não se apaga: arquiva |
| 01/10/2026 | Telefone de responsável | Precisa: muitos pacientes têm cuidador. Ver item 11 |
