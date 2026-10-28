# testes por mutação

cobertura diz quais linhas os testes passam por cima. ela não diz se os testes **pegam** um erro nessas linhas. o jeito de medir isso é o teste por mutação: a gente muda o código de propósito (um mutante), roda os testes e vê se algum falha. mutante que faz os testes falharem foi **morto**; mutante que passa por todos os testes **sobreviveu**. o *mutation score* é a parte dos mutantes mortos.

este documento segue o método de Lu et al., *Beyond Coverage: Automatic Test Suite Augmentation for Enhanced Effectiveness using Large Language Models* (OOPSLA 2026, [doi 10.1145/3798251](https://doi.org/10.1145/3798251)). o artigo mostra que suítes geradas com cobertura perto de 80% ficam com score por volta de 35%, e propõe usar os mutantes que sobrevivem para guiar a escrita de testes novos.

---

## como foi feito

o artigo tem duas partes, e as duas foram aplicadas ao 2ag.

**1. mutantes semânticos (SUNG).** em vez de só trocar operador por operador, como as ferramentas por regra fazem, os mutantes são escritos a partir do que o método faz, em seis frentes:

| frente | o que muda |
| :--- | :--- |
| modelo de comportamento | lista os estados e as transições do método e desvia um pouco de cada um |
| RIP | mutante que a execução alcança mas não infecta o estado, ou infecta sem chegar na saída |
| parecido mas não igual | descreve o comportamento e escreve variantes quase equivalentes |
| onde o erro costuma estar | aponta os trechos com mais chance de bug e faz três mutantes em cada |
| chamada de método | troca argumento, troca o método, tira a chamada, passa nulo |
| condições de borda | limites de faixa, data e quantidade: `<` contra `<=`, vazio, máximo |

cada mutante é um par *antes* / *depois* de uma a três linhas dentro do método. ele é aplicado por substituição direta, os testes do método rodam, o resultado fica anotado e o arquivo volta ao original. mutante que não compila não conta. mutante equivalente (nenhuma entrada mostra a diferença) também não conta e fica registrado com o motivo.

**2. testes guiados pelos sobreviventes (RAGTIME).** os mutantes que sobreviveram são agrupados pela linha que mudam, e os testes novos vêm em três passos:

1. **pelas linhas:** testes que exercitam exatamente as linhas mutadas, com entrada cuja saída depende delas;
2. **por mutante:** para cada sobrevivente, uma frase dizendo como o comportamento muda e testes que falham com o mutante e passam no original;
3. **por imitação:** para o que ainda sobrou, testes imitando os que mataram mutantes do mesmo grupo.

cada teste candidato precisa passar no código original e falhar com pelo menos um sobrevivente aplicado. teste que falha no original ganha até três tentativas de conserto a partir da mensagem de erro; teste que não mata nada é descartado. só os que matam entram na suíte, então ela cresce pouco.

**regra fixa:** nessa etapa só entra código de teste. se um mutante sobrevivente deixar o código *mais* certo do que o original, isso é um bug de verdade e vira card, nunca uma mudança escondida dentro do estudo.

---

## referência por regra: o pit

o artigo compara os mutantes semânticos com o [pit](https://pitest.org), que muta por regra. o 2ag tem um perfil do maven para rodar o pit quando alguém quiser medir de novo. ele não roda no `./mvnw verify` nem no ci, porque leva vários minutos:

```bash
cd backend/doisag
./mvnw -o -Pmutacao test-compile org.pitest:pitest-maven:mutationCoverage -DtimeoutConstant=90000 -DtargetClasses=dev.uffs.doisag.scale.ScaleScorer -DtargetTests=dev.uffs.doisag.scale.ScaleScorerTest
```

o relatório sai em `backend/doisag/target/pit-reports/index.html`. `targetClasses` e `targetTests` aceitam várias classes separadas por vírgula.

medição de 23/10/2026 nas sete classes do backend que entraram no estudo, com os testes delas (607 mutantes por regra):

| classe | mutantes | mortos | sobreviveram | sem cobertura | score |
| :--- | ---: | ---: | ---: | ---: | ---: |
| AppointmentService | 172 | 148 | 11 | 13 | 86,05% |
| ConsentTermService | 60 | 53 | 5 | 2 | 88,33% |
| PasswordService | 12 | 10 | 1 | 1 | 83,33% |
| ReminderService | 59 | 47 | 9 | 3 | 79,66% |
| ScaleScorer | 104 | 81 | 15 | 8 | 77,88% |
| ScaleTaskService | 137 | 114 | 14 | 9 | 83,21% |
| TreatmentProtocolService | 63 | 48 | 10 | 5 | 76,19% |
| **total** | **607** | **501** | **65** | **41** | **82,54%** |

o score conta *sem cobertura* como sobrevivente, como o pit faz. dos 65 que sobreviveram, 19 são chamadas de método sem retorno apagadas (aviso ou trilha que ninguém conferiu), 14 são limites de condição (`<` no lugar de `<=`), 13 são `return true` no lugar da conta e 13 são condições invertidas que nenhum teste distingue.

---

## resultado por método focal

o estudo pegou 13 métodos com regra de negócio: nove do backend e quatro do front. cada mutante foi aplicado sozinho e rodou contra as classes de teste do método duas vezes, uma com os testes da main de antes do estudo e outra com os testes novos. quando outra classe da suíte já testava aquilo (a trilha de auditoria do cancelamento, a corrida da troca de senha com o link de recuperação), ela entrou nas duas rodadas, para um mutante que já morria lá não parecer achado novo.

no front, as rodadas do SUNG caíram e foram refeitas, então cada método ficou com três listas. elas foram juntadas, e mutantes que dão o mesmo código contam uma vez só. a lista de cada método, com o *antes* / *depois*, o resultado nas duas rodadas e o motivo de cada sobrevivente, está em [`mutantes/`](mutantes/).

| método | arquivo | mutantes | equivalentes | score antes | testes novos | score depois |
| :--- | :--- | ---: | ---: | ---: | ---: | ---: |
| `CpfRules.isValid` | dto/CpfRules.java | 18 | 1 | 82,35% | 2 | 94,12% |
| `LoginAttemptLimiter.registerFailure` e `isBlocked` | security/LoginAttemptLimiter.java | 16 | 1 | 46,67% | 6 | 86,67% |
| `PasswordService.changePassword` | service/PasswordService.java | 16 | 0 | 50,00% | 3 | 81,25% |
| `ScaleScorer.withComputedAnswers` | scale/ScaleScorer.java | 16 | 0 | 37,50% | 4 | 62,50% |
| `ConsentTermService.acceptNewVersion` | service/ConsentTermService.java | 10 | 0 | 70,00% | 2 | 90,00% |
| `TreatmentProtocolService.create` | service/TreatmentProtocolService.java | 10 | 0 | 40,00% | 6 | 100,00% |
| `ReminderService.sendAppointmentReminders` | service/ReminderService.java | 10 | 0 | 50,00% | 5 | 100,00% |
| `ScaleTaskService.reopenAfterAnnulment` | service/ScaleTaskService.java | 10 | 1 | 77,78% | 2 | 88,89% |
| `AppointmentService.cancel` | service/AppointmentService.java | 8 | 0 | 37,50% | 4 | 87,50% |
| `requireRole` | app/route-guards.js | 40 | 2 | 63,16% | 5 | 84,21% |
| `findGuardianErrors` | utils/guardian.js | 33 | 1 | 46,88% | 9 | 96,88% |
| `answersPayload` | utils/scale-answers.js | 38 | 1 | 54,05% | 8 | 78,38% |
| `ageFrom` | utils/date-format.js | 39 | 1 | 65,79% | 5 | 92,11% |
| **backend** | | **114** | **3** | **54,95%** | **34** | **86,49%** |
| **front** | | **150** | **5** | **57,93%** | **27** | **87,59%** |
| **total** | | **264** | **8** | **56,64%** | **61** | **87,11%** |

o score é mortos ÷ (mortos + sobreviventes), sem os equivalentes. com 61 testes a mais, a suíte passou a pegar 78 erros plantados que antes passavam direto.

o pit, que muta por regra, deu 82,54% nas sete classes medidas em 23/10. nos métodos do backend deste estudo, os mutantes semânticos deram 54,95% com os testes de antes. é o que o artigo diz: as trocas de operador do pit são fáceis de matar, e os mutantes escritos a partir do comportamento acham buracos que elas não mostram.

### o que ficou vivo e por quê

33 mutantes continuam vivos. nenhum deles é um erro que a pessoa usando o sistema consiga provocar hoje:

- **entrada que o sistema não produz.** a maioria muda o resultado só para um valor que nenhuma tela manda: telefone só de pontuação, e-mail com maiúscula que o cadastro já normalizou, número com letra num campo `type=number`, versão do termo com espaço em volta, campo calculado do diário do sono preenchido pelo cliente, status `EM_ANDAMENTO` que nenhum código grava.
- **diferença que ninguém observa.** o mapa devolvido é o mesmo objeto ou uma cópia, a ordem das chaves muda, o `save` repete o que a transação já grava, o status do redirect do loader (301, 302 ou 307) que o roteador do navegador segue do mesmo jeito.
- **mutante mais certo que o original.** esses viraram card em vez de teste, como a regra fixa manda:
  - [#177](https://github.com/2ag-uffs/2ag/issues/177): resposta só de espaço num item numérico da escala vai como 0, contra a RN10, e o horário vai sem `trim`;
  - [#178](https://github.com/2ag-uffs/2ag/issues/178): data de nascimento no futuro gera idade negativa, e na meia-noite exata o hoje sai do fuso do navegador e não do de brasília.

a revisão do `requireRole` achou mais uma fragilidade fora do método: uma sessão com papel que o front não conhece deixa o `/entrar` redirecionando para ele mesmo ([#179](https://github.com/2ag-uffs/2ag/issues/179)).

### o que a revisão tirou

cada lote de testes novos passou por um revisor que tentou derrubar os testes, e o conjunto todo passou por uma revisão final. saíram os testes que matavam mutante sem testar algo que importa:

- teste que só matava o mutante com entrada impossível (endereço nulo, senha maior que o limite do DTO, e-mail com maiúscula, espaço unicode no CPF);
- teste que conferia a ordem interna das chamadas num mock, quando a corrida de verdade já está coberta em outra classe;
- teste repetido de outra classe da suíte (trilha de auditoria do cancelamento, paciente arquivado no acompanhamento);
- status do redirect do loader, que o navegador nunca vê.

a conferência do texto exato de uma exceção virou conferência só das contagens. os mutantes dos testes tirados voltaram para a conta como sobreviventes, e por isso alguns scores ficaram abaixo de 100%: um número honesto vale mais que um teste que trava uma refatoração sem proteger ninguém.

---

## como repetir

as listas em [`mutantes/`](mutantes/) servem de ponto de partida: cada mutante tem a linha, o *antes* e o *depois*. `...` dentro do *antes* ou do *depois* quer dizer "o resto da linha fica igual", e `(line removed)` ou `(chamada removida)` quer dizer apagar o trecho.

1. escolha um método com regra de negócio e testes próprios.
2. escreva os mutantes nas seis frentes da tabela acima, cada um como *antes* / *depois*.
3. aplique um de cada vez, rode só os testes do método (`./mvnw -o -q test -Dtest=Classe` ou `npx vitest run arquivo`) e anote morto ou sobreviveu. desfaça com `git checkout -- arquivo`.
4. para cada sobrevivente, escreva o teste que o mata. confira que ele passa no original e falha com o mutante.
5. score = mortos ÷ (mortos + sobreviventes), antes e depois. mutante equivalente fica fora da conta.
