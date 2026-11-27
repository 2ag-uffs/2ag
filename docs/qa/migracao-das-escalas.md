# migração das escalas do papel para a tela

conferência de 20/11/2026, feita depois da pesquisa de tendências de 19/11/2026. cada formulário que a clínica aplica está em [`docs/scales`](../scales). este documento compara o que está no papel com o que o paciente lê na tela (o catálogo em `backend/doisag/src/main/java/dev/uffs/doisag/scale/ScaleCatalog.java`) e diz, sem enfeite, o que está atendido e o que não está na passagem do papel para a tela.

**como foi feita:** o texto dos PDFs saiu com `pdftotext -layout`. as tabelas de frequência dos formulários de dor e de TEA e o formulário de Hamilton inteiro são imagens, e foram conferidos no olho, com o PDF aberto ao lado do catálogo. o papel da clínica foi a única fonte desta conferência: nenhum texto novo veio de outra versão do instrumento.

**a regra:** o que o paciente lê como pergunta só pode ficar mais perto do papel, nunca mais longe. as chaves dos itens, as opções (texto, ordem e valor), a ordem dos itens, o cálculo do escore e as faixas não mudaram, e os testes do escore de referência continuam passando sem mexer neles.

**o nome curto:** nomes como *Qualidade do sono de uma maneira geral* e *Intensidade da dor no período* são do sistema, não do papel. o paciente lê a pergunta inteira do papel no lugar deles, do mesmo tamanho e peso. o nome curto ficou só onde é preciso um nome curto: o gráfico, os arquivos CSV, a lista *Falta preencher*, o botão *Limpar resposta* e a leitura da resposta salva.

---

## 1. Pittsburgh (PSQI-BR), `psqi-br.pdf`

| item | papel | tela | igual ou o que difere |
| :--- | :--- | :--- | :--- |
| título | ÍNDICE DA QUALIDADE DO SONO DE PITTSBURGH (PSQI-BR) | Índice de qualidade do sono de Pittsburgh (PSQI-BR) | difere: *de* no lugar de *da* e sem caixa alta. é o nome da escala no sistema inteiro (tarefas, avisos, arquivos), e não é pergunta |
| instrução | As seguintes perguntas são relativas aos seus hábitos de sono durante o último mês somente. Suas respostas devem indicar a lembrança mais exata da maioria dos dias e noites do último mês. Por favor, responda a todas as perguntas. | igual | igual. antes a tela trazia uma versão reescrita, sem o *Por favor, responda a todas as perguntas* |
| 1 | Durante o último mês, quando você geralmente foi para a cama a noite? · campo *Hora usual de deitar:* | Durante o último mês, quando você geralmente foi para a cama à noite? · *Hora usual de deitar:* | igual, com o acento corrigido |
| 2 | Durante o último mês, quanto tempo (em minutos) você geralmente levou para dormir a noite? · campo *Número de minutos:* | …para dormir à noite? · *Número de minutos:* | igual, com o acento corrigido. nome curto: *Número de minutos para dormir* |
| 3 | Durante o último mês, quando você geralmente levantou de manhã? · campo *Hora usual de levantar:* | igual | igual |
| 4 | Durante o último mês, quantas horas de sono você teve por noite? (Esta pode ser diferente do número de horas que v ocê ficou na cama) · campo *Horas de sono por noite:* | …que você ficou na cama) · *Horas de sono por noite:* | igual, com a letra solta corrigida. a nota entre parênteses é do papel |
| antes da 5 | Para cada uma das questões restantes, marque a melhor (uma) resposta . Por favor, responda a todas as questões. | …a melhor (uma) resposta. Por favor… | igual, sem o espaço antes do ponto. aparece uma vez, antes da pergunta 5; no celular, só na tela do item 5a |
| 5 | Durante o último mês, com que frequência você teve dificuldade para dormir porque você: | igual | igual. fica uma vez em cima dos itens de a até j e, no celular, em cima de cada um deles |
| 5a a 5i | A) Não conseguiu adormecer em até 30 minutos · B) Acordou no meio da noite ou de manhã cedo · C) Precisou levantar para ir ao banheiro · D) Não conseguiu respirar confortavelmente · E) Tossiu ou roncou forte · F) Sentiu muito frio · G) Sentiu muito calor · H) Teve sonhos ruins · I) Teve dor | igual, sem as letras | igual. a tela não mostra as letras de A a I |
| 5j, texto | J) Outras razões, por favor descreva: | Outras razões, por favor descreva: | igual |
| aviso do sistema, na 5j | não tem | Isto não é atendimento de urgência. Se algo piorou, fale com a clínica ou procure um serviço de saúde. | é do sistema, não do papel. fica embaixo do campo de texto da 5j, como embaixo de todo texto livre que o paciente escreve |
| 5j, frequência | Com que frequência, durante o último mês, você teve dificuldade para dormir devido a essa razão? | igual | igual. o título da pergunta 5 fica em cima e não se repete dentro do item |
| 6 | Durante o último mês como você classificaria a qualidade do seu sono de uma maneira geral: | igual | igual |
| 7 | Durante o último mês, com que frequência você tomou medicamento (prescrito ou “por conta própria”) para lhe ajudar | …para lhe ajudar a dormir? | difere de propósito. no papel a frase acaba em *para lhe ajudar*, sem dizer a quê e sem ponto de interrogação; a tela completa com *a dormir?* (decisão da dona do projeto, novembro de 2026) |
| 8 | No último mês, que frequência você teve dificuldade para ficar acordado enquanto dirigia, comia ou participava de uma atividade social (festa, reunião de amigos) | No último mês, com que frequência você teve dificuldade… (festa, reunião de amigos)? | difere de propósito. o papel escreve *que frequência*, sem o *com*, e não fecha a pergunta; a tela põe o *com* e o ponto de interrogação (decisão da dona do projeto, novembro de 2026) |
| 9 | Durante o último mês, quão problemático foi pra você manter o entusiasmo (ânimo) para fazer as coisas (suas atividades habituais) | …(suas atividades habituais)? | difere de propósito: a tela fecha a pergunta com o ponto de interrogação que o papel não traz (decisão da dona do projeto, novembro de 2026) |
| 10 | Você tem um parceiro (a), esposo (a) ou colega de quarto? | igual | igual |
| opções da 5, da 7 e da 8 | Nenhuma no último mês · Menos de uma vez por semana · Uma ou duas vezes por semana · Três ou mais vezes na semana | igual | igual. o papel traz o número de 0 a 3 embaixo de cada opção, e a tela não mostra |
| opções da 6 | Muito boa · Boa · Ruim · Muito Ruim | Muito boa · Boa · Ruim · Muito ruim | difere só na maiúscula de *Ruim*. opção não muda nesta rodada |
| opções da 9 | Nenhuma dificuldade · Um problema leve · Um problema razoável · Um grande problema | igual | igual |
| opções da 10 | Não · Parceiro ou colega, mas em outro quarto · Parceiro no mesmo quarto, mas em outra cama · Parceiro na mesma cama | igual | igual |

a ficha em papel não traz a conta do índice nem o corte: eles vêm do Anexo A.2 de [`docs/requisitos-v2.md`](../requisitos-v2.md).

## 2. acompanhamento semanal de dor, `registro-dor.pdf`

| item | papel | tela | igual ou o que difere |
| :--- | :--- | :--- | :--- |
| título | Acompanhamento semana de paciente com dor | Acompanhamento semanal de dor | difere. é o nome da escala no sistema inteiro (tarefas, avisos, arquivos), e não é pergunta |
| período | `Período: De __/__/__ A __/__/__` | *Início do período* e *Fim do período* | difere na forma: dois campos de data |
| subtítulo | Qualidade de vida | não aparece | difere. o papel de dor traz esse subtítulo em cima da pergunta da dor |
| frase do sistema | não tem | Pense na última semana para responder. | é do sistema, não do papel. fica no cabeçalho da página, embaixo do título, fora dos itens do formulário |
| intensidade | De acordo com a escala visual abaixo, como você classifica a sua dor no ultimo período? | …no último período? | igual, com o acento corrigido. nome curto: *Intensidade da dor no período* |
| escala visual | LEVE de 0 a 2, MODERADA de 3 a 7, INTENSA de 8 a 10, com rostos e cores | Dor leve 0 a 2 · Dor moderada 3 a 7 · Dor intensa 8 a 10 | difere. o nome da faixa leva *Dor* na frente e a tela não tem os rostos. os cortes são os mesmos. faixa não muda nesta rodada |
| cabeçalho da tabela | Durante a última semana com que frequência a dor te atrapalhou: | igual | igual. fica uma vez em cima das cinco linhas. antes vinha reescrito (*Durante a última semana, com que frequência a dor atrapalhou*), embaixo de cada linha |
| linha 1 | Atividades básicas (comer, levantar, tomar banho) | igual | igual |
| linha 2 | Atividades sociais (sair com amigos, passear, ficar com a família) | igual | igual |
| linha 3 | Na produtividade do trabalho | igual | igual. nome curto: *Produtividade no trabalho* |
| linha 4 | No seu sono | igual | igual. nome curto: *Sono* |
| linha 5 | Necessitou de medicação extra para dor | igual | igual |
| opções | Nenhum dia · Até 3 dias · Entre 3 e 6 dias · Todos os dias | igual | igual |
| observações | Observações: Escreva aqui qualquer ponto que julgar relevante como, por exemplo, o relato de algum efeito colateral, a ausência da tomada de alguma medicação, o relato de algum momento significativo na rotina, etc... | igual | igual. antes a tela trazia um resumo. embaixo vem o aviso do sistema de que ali não é atendimento de urgência |

## 3. acompanhamento semanal de TEA, `registro-tea.pdf`

| item | papel | tela | igual ou o que difere |
| :--- | :--- | :--- | :--- |
| título | ACOMPANHAMENTO SEMANAL DE PACIENTE COM TEA | Acompanhamento semanal de TEA | difere. é o nome da escala no sistema inteiro, e não é pergunta |
| período | `Período: De __/__/__ A __/__/__` | *Início do período* e *Fim do período* | difere na forma: dois campos de data |
| subtítulo | Qualidade de vida | não aparece | difere |
| frase do sistema | não tem | Pense na última semana para responder. | é do sistema, não do papel. fica no cabeçalho da página, fora dos itens do formulário |
| qualidade de vida | Em uma escala de 0 a 10, sendo 10 a melhor pontuação, como você classifica sua qualidade de vida no último período? | igual | igual. nome curto: *Qualidade de vida no período* |
| escala visual | cinco rostos, do vermelho no 0 ao verde no 10, sem palavras | régua de 0 a 10 com *0 muito ruim* e *10 muito boa* | difere. as palavras das pontas são do sistema, e a tela não tem os rostos |
| cabeçalho da tabela | Durante a última semana com que frequência esses comportamentos ocorreram: | igual | igual. fica uma vez em cima dos seis comportamentos |
| agressividade | AGRESSIVIDADE/IMPULSIVIDADE · Agrediu verbalmente ou fisicamente a si ou outras pessoas? Quebrou objetos de forma impulsiva? | Agressividade e impulsividade · descrição igual | descrição igual (antes a tela dizia *a si ou a outras pessoas*). o título difere: a tela põe a palavra *e* onde o papel tem a barra, e não usa caixa alta. o título já era assim antes desta rodada e não mudou |
| agitação | AGITAÇÃO PSICOMOTORA/ANSIEDADE · Não conseguiu ficar quieto(a) / parado(a) por tempo curto? Demonstrou sinais de nervosismo, como falta de ar, inquietação, expressão tensa? | Agitação psicomotora e ansiedade · descrição igual | descrição igual (antes a tela dizia só *ficar parado*). título como o de cima |
| sono | SONO · Demorou para pegar no sono, acordou ou se manteve acordado(a) durante a noite? Sonolento(a) durante o dia? | Sono · descrição igual | igual (antes a tela vinha sem os *(a)*) |
| interação social | INTERAÇÃO SOCIAL · Permaneceu isolado(a), com pouco contato com outras crianças? | Interação social · Permaneceu isolado(a), com pouco contato com outras pessoas? | difere de propósito: o papel diz *outras crianças* e a tela diz *outras pessoas*, porque o formulário também é respondido por paciente adulto (decisão de 20/11/2026) |
| estereotipia | ESTEREOTIPIA · Realizou movimentos repetitivos estereotipados? | igual | igual |
| apetite | APETITE · Perdeu ou ganhou peso, ou teve alterações no tipo de comida/alimentos de que gosta? | Apetite · descrição igual | igual (antes a tela vinha sem o */alimentos*) |
| opções | Nenhum dia · Até 3 dias · Entre 3 e 6 dias · Todos os dias | igual | igual |
| observações | o mesmo texto do formulário de dor | igual | igual |

## 4. erros de digitação do papel corrigidos na tela

só erro evidente de digitação foi corrigido. são cinco:

1. Pittsburgh, pergunta 1: *a noite* virou *à noite*.
2. Pittsburgh, pergunta 2: *a noite* virou *à noite*.
3. Pittsburgh, pergunta 4: *v ocê* (letra solta da palavra) virou *você*.
4. Pittsburgh, frase antes da pergunta 5: *resposta .* (espaço antes do ponto) virou *resposta.*
5. dor, pergunta da intensidade: *ultimo período* virou *último período*.

o que não é erro de digitação foi copiado como está no papel, e fica para a clínica confirmar: a falta de vírgula depois de *Durante o último mês* na pergunta 6 e de *Durante a última semana* nos cabeçalhos de dor e de TEA. A única palavra trocada de propósito é *outras pessoas* no lugar de *outras crianças* no formulário de TEA (decisão de 20/11/2026).

**perguntas 7, 8 e 9 do Pittsburgh: a tela difere do papel de propósito.** na conferência de 20/11/2026 as três tinham sido copiadas com as falhas do papel: a 7 acabava em *para lhe ajudar*, a 8 dizia *que frequência* sem o *com*, e nenhuma das três fechava com ponto de interrogação. a dona do projeto decidiu, em novembro de 2026, corrigir o português na tela, para o paciente não estranhar uma frase que parece incompleta. a tela agora diz:

- 7: *Durante o último mês, com que frequência você tomou medicamento (prescrito ou “por conta própria”) para lhe ajudar a dormir?*
- 8: *No último mês, com que frequência você teve dificuldade para ficar acordado enquanto dirigia, comia ou participava de uma atividade social (festa, reunião de amigos)?*
- 9: *Durante o último mês, quão problemático foi pra você manter o entusiasmo (ânimo) para fazer as coisas (suas atividades habituais)?*

só a frase da pergunta mudou. o nome curto de cada item, as opções (texto, ordem e valor), a ordem dos itens e o escore continuam os mesmos (teste `asPerguntas7A9DoPittsburghVemComOPortuguesCorrigido`, em `ScaleCatalogTest`). o papel de [`docs/scales`](../scales) não foi mexido, então quem confere a tela com a folha (CT-25) encontra essas três diferenças, e elas não são defeito.

## 5. o que foi comparado e não mudou nesta rodada

o MEEM não foi mexido: é aplicado pela prescritora na consulta, e o paciente não vê.

**Hamilton (`ham-a.pdf`, imagem conferida no olho).** o nome e a descrição dos itens 1, 2, 4 a 10 e 12 são iguais aos do papel. o que difere:

- o título do papel é *ESCALA DE AVALIAÇÃO DE ANSIEDADE DE HAMILTON HAM-A*, e a tela diz *Escala de ansiedade de Hamilton (HAM-A)*, sem o *de avaliação*, com o *HAM-A* entre parênteses e sem caixa alta. é o nome da escala no sistema, e não é pergunta.
- o papel abre com um parágrafo que apresenta a escala (*Essa escala é um método objetivo e de fácil operacionalização para avaliação da ansiedade...*), e a tela não traz esse parágrafo.
- a instrução do papel é *Cada um dos itens deve ser respondido com o score de 0-4, onde valores maiores indicam sintomas mais severos de ansiedade.*, e a tela diz *Cada item vai de 0 a 4, e valores maiores indicam sintomas mais severos de ansiedade.*
- item 3, *Medos*: o papel acaba com *(avaliar qualquer um por intensidade e frequência de exposição)*, e a tela não traz esse trecho.
- item 11: o papel repete *náusea, vômitos* no fim da lista, e a tela não repete.
- item 13: o papel escreve *tonturas, etc.*, e a tela *tonturas etc.*
- item 14: o papel traz *agitação das mãos (tremores, remexer, cacoetes)* e *dilatação pupilar*, e a tela não traz esses dois trechos.
- o papel tem a coluna *GRAU* para escrever de 0 a 4, sem palavras. *0 ausente* e *4 muito grave* são do sistema.
- o resultado do papel diz *acima de 26 - ansiedade grave*, e a tela põe o 26 na faixa grave (26 a 56).
- o papel é de entrevista: a coluna se chama *COMPORTAMENTO*, o item 14 é *Comportamento na entrevista* e quem preenche é quem aplica. na tela quem responde é o paciente.

**acompanhamento semanal (`acompanhamento-semanal.pdf`).** as âncoras de cada nota (*0 (sem dor) a 10 (dor intensa)* e as outras) são iguais às do papel. o que difere:

- o nome de alguns parâmetros foi escrito por extenso: *N.º gotas manhã* virou *N.º de gotas pela manhã*, *N.º gotas tarde* virou *N.º de gotas à tarde*, *Disposição/Energia* virou *Disposição e energia*, *Rigidez/Espasticidade* virou *Rigidez e espasticidade* e *Diminuição de Maconha Fumada/Outra Substância* virou *Diminuição de maconha fumada ou outra substância*.
- *Doença dermatológica*, que no papel é uma linha só com *Especificar e Intensidade (0-10)*, virou dois itens na tela, e as pontas *0 nenhuma* e *10 intensa* da intensidade são do sistema.
- o texto das observações continua resumido na tela (*Relato de efeito colateral, ausência da tomada de alguma medicação, momento significativo na rotina*). no papel é a frase inteira, sem o título *Observações*: *Escreva aqui qualquer ponto que julgar relevante como, por exemplo, relato de algum efeito colateral, a ausência da tomada de alguma medicação,o relato de algum momento significativo na rotina, etc…*. ela é parecida com a dos formulários de dor e de TEA, mas não é a mesma: não tem o *o* antes do primeiro *relato*, não tem espaço depois de *medicação,* e acaba com reticências de um caractere só. quem trouxer a frase do papel para a tela copia esta, e não a da dor e do TEA.
- embaixo dos dois campos de texto livre, o da doença dermatológica e o das observações, vem o aviso do sistema de que ali não é atendimento de urgência.
- a instrução *Preencha um dia de cada vez, como na ficha em papel. O que você não responder fica em branco, e em branco não vale zero.* é do sistema.
- o papel tem cinco colunas de dia, e a tela mostra os dias do período.

**diário do sono (`diario-sono.pdf`).** os nomes da maior parte dos itens são os do papel. o que difere:

- quase todos os textos de apoio foram encurtados ou reescritos. por exemplo: *Horário que apagou as luzes no dia anterior (ontem)* virou *Horário em que apagou as luzes*; *Some os minutos em que você acordou à noite* virou *Some os minutos em que ficou acordado à noite*; *Atividades como caminhada ou hidro em min.* virou *Caminhada ou hidroginástica, em minutos*; *Horas que passou fora de casa, em horas* virou *Em horas*; e os textos de *Bebida alcoólica*, *Cochilos durante o dia*, *Café* e *Cigarro à noite* foram reescritos sem o *Anote* e sem o *você*.
- *Desatenção (falta de concentração)* aparece na tela só como *Desatenção*.
- *Dia comum (sim / não)* e *Anote no verso porque não foi um dia comum* viraram a pergunta *Foi um dia comum* e o campo *Por que não foi um dia comum*; *Medicação para dormir · Medicação que causa sono (sim/não)* virou *Tomou medicação que causa sono*.
- embaixo do campo *Por que não foi um dia comum* vem o aviso do sistema de que ali não é atendimento de urgência.
- as pontas de *Irritabilidade* e de *Dor* estão no papel como *Nenhum* e *Muito*, e na tela como *nenhuma* e *muita*.
- o aviso *ATENCAO: se acordar no meio da noite sair da cama.*, que no papel fica no item 7, está na instrução do alto da tela.
- os itens 3, 7 e 8 (tempo na cama, total acordado e tempo total de sono) o papel pede para o paciente calcular, e na tela é o sistema que calcula. a coluna *Média* do papel não existe na tela.

## 6. cuidados da passagem do papel para a tela

a lista abaixo são os cuidados que a pesquisa de tendências de 19/11/2026 levantou para quando um questionário sai do papel e vai para a tela. ela diz o que foi conferido no código e na tela em 20/11/2026. não é uma declaração de conformidade com nenhuma norma ou guia.

**atendidos**

- no celular, uma pergunta por tela, e a pergunta que o papel usa para juntar vários itens aparece em cima de cada um deles.
- no Pittsburgh, na dor e no TEA, cada item traz a pergunta inteira do papel, com o período de que ela fala (*Durante o último mês*, *Durante a última semana*, *no último período*).
- nada vem marcado. o item sem resposta fica em branco e não vira zero, e *Limpar resposta* desfaz uma escolha.
- a tela não passa sozinha para a próxima pergunta: é a pessoa que toca em *Próxima*, e *Anterior* volta mostrando o que estava marcado.
- as opções vêm em lista, uma embaixo da outra, na ordem e com o texto do papel, e só uma pode ser escolhida.
- os cartões de opção de uma mesma pergunta têm todos a mesma altura, e o cartão que se vê é a área de toque inteira. medido no Chrome sem janela em 375 e em 320 px, nos temas claro e escuro: na pergunta 10 do Pittsburgh os quatro cartões têm 74 px (antes, dois tinham 50 px), e num item do TEA e na escolaridade do MEEM todos têm 50 px.
- o texto de apoio tem o tamanho do texto da página, 16 px (antes, 14 px). em várias escalas ele é texto do próprio instrumento.
- no celular, as onze notas da régua de dor têm todas o mesmo tamanho, 44 por 48 px, com as faixas cada uma na sua linha como antes. medido no Chrome sem janela em 320 e em 375 px, nos temas claro e escuro (antes, em 375 px, as notas de 0 a 2 e de 8 a 10 tinham 92 px de largura e as de 3 a 7 tinham 52 px).
- a borda dos campos, dos cartões de opção, da bolinha vazia e das notas das réguas tem contraste de pelo menos 3:1 com o fundo, nos temas claro e escuro, também em cima das três faixas coloridas da régua de dor (a menor é 3,2:1, sobre a faixa vermelha no tema claro; antes era 1,3:1 sobre o cartão). conta da WCAG refeita pelo teste `frontend/src/styles/control-border.test.js` e cor conferida no Chrome sem janela.
- numa janela baixa, com menos de 480 px de altura, como o celular deitado ou o computador com zoom de 400%, o cabeçalho, o contador, os botões e o menu de baixo deixam de ficar presos e rolam com a página, então nenhuma barra cobre a pergunta. medido no Chrome sem janela em 320x256 e em 667x375: antes as barras cobriam de 249 a 256 dos 256 px de altura e 258 dos 375; depois, nenhum. o celular deitado é uma saída para quem precisa: as escalas foram pensadas para o celular em pé.
- a ordem dos itens e as opções são as do papel, fora as diferenças de forma anotadas nas tabelas, e o cálculo e as faixas são os do papel ou do Anexo A dos requisitos, conferidos pelos testes do escore de referência. nada disso mudou nesta rodada.
- a resposta salva só é corrigida pelo paciente até a prescritora analisar; a versão anterior fica guardada, e anular pede motivo.

**não atendidos**

- confirmação dos itens deixados em branco: a tela não pergunta se o item ficou em branco de propósito.
- tela final de revisão: não há uma tela para conferir as respostas antes de salvar.
- treino no aparelho: não há pergunta de exemplo nem instrução de uso antes da primeira escala.
- Hamilton respondido pelo paciente: o papel é preenchido por quem entrevista. mudar quem responde não é só mudança de apresentação.

**pendentes**

- permissão e licença de uso dos instrumentos: a confirmar com a clínica.
- as frases do papel copiadas como estão, listadas no fim da seção 4: a confirmar com a clínica.
- as diferenças do Hamilton, do acompanhamento semanal e do diário do sono listadas na seção 5: não foram mexidas nesta rodada.
- Safari do iPhone: não foi testado com as ferramentas do projeto, que só rodam o Chrome. é o caso CT-52 do [plano de testes](plano-de-testes.md), manual e ainda não executado.
