# perguntas para a clínica

Dúvidas que apareceram durante o desenvolvimento e que **não dá para responder do lado técnico** — dependem de como a prescritora trabalha de fato. Cada uma trava ou enviesa uma decisão de modelagem.

Serve também como registro de diário de bordo: o que foi perguntado, quando e o que ela respondeu.

---

## 1. Composição do óleo prescrito

**Status:** aberta · **trava:** modelagem da prescrição (RN03)

A tela de prescrição oferece cinco opções fixas de formulação: `CBD Isolado`, `CBD Broad Spectrum`, `CBD Full Spectrum`, `THC Isolado`, `THC + CBD`. E aceita **um único** número de concentração.

Há indício de que isso não corresponde à prática: a prescrição parece envolver **percentuais de cada canabinoide, definidos caso a caso**, porque a resposta varia por pessoa e por condição tratada.

Se for isso, a lista fixa impede o registro correto — "CBD Full Spectrum" não diz se é 10% ou 20%, nem quanto de THC tem dentro.

**Perguntar:**

- Quando você prescreve, você escolhe o **canabinoide** e o **espectro** separadamente, ou pensa nas combinações já prontas?
- Você define a concentração de cada canabinoide (ex.: CBD 10%, THC 0,3%), ou é uma concentração só do produto?
- A concentração vai em percentual, em mg/mL, ou nos dois?
- O volume do frasco importa para o registro, ou basta a concentração?
- Existe lote do produto que você precise anotar? *(a tabela de necessidades de 2025 menciona lote)*

**Por que importa:** hoje só cabe um número. Se forem dois ou três canabinoides com percentuais próprios, a prescrição precisa de uma lista, como já foi feito com o escalonamento de dose.

---

## 2. Sinais vitais na consulta

**Status:** assumido, confirmar · **afeta:** modelo da consulta (RF04)

A tela de consulta já coletava pressão arterial, peso e altura, mas o modelo não tinha onde guardar e esses dados se perdiam. Foram acrescentados, com peso e altura como número para permitir acompanhar ao longo do tratamento.

**Perguntar:**

- Você mede pressão, peso e altura em toda consulta, ou só em alguns casos?
- Acompanhar a variação de peso ao longo do tratamento é útil para você?
- Falta algum outro sinal que você registra e não está na tela?

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

Já respondido: **todas as oito escalas são essenciais**, nenhuma sai do piloto.

**Perguntar:**

- Podemos começar lançando dados retrospectivos de poucos pacientes, a partir das planilhas que você já tem? É o que faz o gráfico de evolução ter curva real desde o começo.
- Você consegue nos passar uma planilha preenchida **com os identificadores removidos**, para planejarmos a importação?
- Quais pacientes você convidaria para usar o sistema de verdade? Eles precisam concordar por escrito.
- Os quatro que responderam o formulário em 2025 seriam um bom ponto de partida?

---

## 5. Consentimento e formalização

**Status:** aberta · **trava:** o piloto não pode começar sem isso

- A clínica assina um termo de parceria com a universidade? *(a UFFS deve ter modelo)*
- Como você prefere apresentar o consentimento aos pacientes?
- Há algum dado que você **não** quer que saia do prontuário atual para o sistema novo?

---

## 6. Quem aplica a escala de Hamilton

**Status:** aberta · **afeta:** RF21 e RN09

No instrumento original a HAM-A é pontuada por quem entrevista, e o item 14 é "comportamento durante a entrevista", que só o avaliador observa. O sistema hoje entrega a escala para o paciente responder sozinho.

**Perguntar:**

- Você aplica a HAM-A em entrevista, pontuando cada item, ou o paciente responde sozinho?
- Se o paciente responde, como fica o item 14?

**Por que importa:** se a aplicação é em entrevista, a HAM-A passa para o lado do prescritor, como o MEEM, e deixa de virar tarefa do paciente.

---

## 7. Como a consulta é marcada

**Status:** aberta · **afeta:** RF10 e RF11

A proposta é que o prescritor cadastre os horários disponíveis, o paciente solicite um horário e o prescritor confirme.

**Perguntar:**

- Você prefere confirmar cada solicitação ou deixar o paciente marcar direto?
- Quais dias e horários você atende? Isso muda de semana para semana?
- Quanto tempo dura cada tipo de consulta (primeira consulta, retorno)?

---

## 8. Agenda do Amplimed

**Status:** aberta · **afeta:** escopo da agenda (RF10 e RF11)

**Perguntar:**

- A agenda do Amplimed já está em uso na clínica?
- Se estiver, faz sentido manter uma segunda agenda no 2ag, ou basta registrar as consultas que já aconteceram?

**Por que importa:** manter duas agendas obriga a lançar cada horário duas vezes, que é justamente o retrabalho que o sistema existe para eliminar.

---

## 9. Lembrete de dose

**Status:** aberta · **afeta:** RF34

**Perguntar:**

- Os pacientes usariam notificação no celular, e-mail ou outro canal para lembrar da dose?
- Os horários de dose são fixos por prescrição, ou cada paciente ajusta à própria rotina?

---

## 10. Termo de consentimento do sistema

**Status:** aberta · **trava:** RF36 e o início do piloto

O cadastro já pede o aceite de um termo de consentimento e guarda a versão e a data de cada aceite. O texto atual é um **rascunho técnico** e precisa ser revisado e aprovado pela clínica, de preferência com apoio jurídico, antes de qualquer paciente real.

**Perguntar:**

- A clínica já usa um termo de consentimento em papel? Podemos partir dele?
- Quem responde como encarregado de dados da clínica (LGPD, art. 41) e qual contato deve aparecer no termo?
- Os dados do sistema poderão ser usados em pesquisa, mesmo sem identificação? Se sim, isso pede um consentimento separado.

---

## respondidas

| Quando | Pergunta | Resposta |
| :--- | :--- | :--- |
| set/2026 | Quais escalas entram no piloto? | Todas as oito. Nenhuma é dispensável |
