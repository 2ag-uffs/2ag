# guia de mensagens de erro

Quem lê as mensagens de erro do 2ag não é desenvolvedor: é o paciente (muitas vezes mais velho, às vezes com pouca escolaridade, quase sempre no celular, às vezes um responsável respondendo por uma criança), o prescritor no meio da consulta e a administração da clínica. Toda mensagem de erro, da api ou da tela, passa pela régua abaixo.

## a régua

1. **O que aconteceu, depois o que fazer.** Quando a pessoa consegue resolver, a solução está na mensagem: o formato com um exemplo, o limite, o que falta, onde tocar. Quando não consegue, a mensagem diz isso e diz quem resolve (o prescritor, a clínica) ou quando tentar de novo.
2. **As palavras da tela.** O campo ou a coisa tem o nome que o rótulo da tela dá. Nada de palavra técnica: requisição, rota, registro (no sentido de linha do banco), corpo, JSON, método, token, payload, id, servidor (quando "o sistema" serve), autenticar, sessão inválida, degrau (a tela diz Semana), nulo, formato inválido.
3. **Nunca só "inválido", "erro", "falha" ou "obrigatório".** Diga o que se espera: "Informe o e-mail"; "Confira o e-mail: ele precisa ter um @ e o endereço depois dele, como nome@exemplo.com"; "Escolha uma data a partir de hoje".
4. **Calma e sem culpa.** Sem ponto de exclamação, sem maiúsculas para dar ênfase, sem "você errou", "ilegal" ou "proibido". "Não foi possível ..." serve quando o motivo ou o próximo passo vem logo depois.
5. **Curta.** Frases de até umas 20 palavras, no máximo duas, no presente e com "você". Limite se diz com o número.
6. **Um erro, uma frase.** A validação da tela e a da api para a mesma regra dizem a mesma frase, e a mesma regra em dois formulários também. Entre "O e-mail é obrigatório" e "Informe o e-mail", fica a forma "Informe ...".
7. **A pontuação dos vizinhos.** As mensagens da api não têm ponto final, as da tela têm, e duas frases se separam por ponto. Não troque uma mensagem só por causa da pontuação.
8. **Se já passa, fica.** Nada de reescrever por gosto.

## o que não muda, por segurança

- Entrar, criar conta, convite, senha nova, troca e confirmação de e-mail continuam sem dizer se uma conta, um e-mail ou um CPF existe. E-mail errado e senha errada recebem a mesma frase. Mais clara nunca quer dizer mais reveladora: na dúvida, a mensagem continua tão genérica quanto era e só ganha palavras mais gentis.
- A resposta de quem não tem permissão, ou do que é de outra pessoa, não revela nada sobre o que existe para os outros.
- Nenhuma mensagem traz nome de classe, pilha, SQL, caminho, número interno ou o que foi digitado num campo de senha.
- Nada clínico em mensagem que aparece antes de entrar ou que vai por e-mail.
- Só o texto muda. O status HTTP, a chave do campo com erro, o formato do JSON de erro, o nome dos cabeçalhos, a exceção lançada e o momento em que ela acontece ficam iguais, e a mensagem nova nunca muda o campo marcado na tela.

## fora desta régua

O texto das escalas (itens, opções, instruções, faixas e tudo o que o paciente lê no `ScaleCatalog`), o termo de consentimento e o de pesquisa, assunto e corpo de e-mail, avisos que não são de erro, as linhas de log escritas para quem desenvolve e os comentários do código. A mensagem sobre uma resposta, como "Número alto demais para este campo. O máximo é 200", entra na régua; o texto da escala, não.

## como trocar uma mensagem

1. Troque o texto onde ele é definido. Na api, ela está numa constante pública (como `ErrorHandler.FORBIDDEN_MESSAGE`), no `message` da anotação de validação do DTO ou no texto do `throw`.
2. Procure o texto antigo no repositório inteiro e atualize cada lugar que o cita: os testes do backend, os testes e os mocks do front que repetem a mensagem da api, os [requisitos](./requisitos-v2.md), o [plano de testes](./qa/plano-de-testes.md), os READMEs e a [capacitação](./capacitacao/). Nenhum documento nem teste fica com a frase antiga.
3. O teste que fixa a mensagem passa a fixar o texto novo. Nenhum teste é apagado nem afrouxado.

## antes e depois

Exemplos da revisão de 20/11/2026.

| onde | antes | depois | o que mudou |
| :--- | :--- | :--- | :--- |
| api, erro que ninguém previu (`500`) | Ocorreu um erro inesperado no servidor | Houve um problema no sistema. Tente de novo em alguns minutos e, se continuar, fale com a clínica | "servidor" virou "sistema", e a pessoa fica sabendo o que fazer e quem procurar |
| api, sem permissão (`403`) | Acesso negado | Sua conta não tem permissão para isso. Se precisar, fale com a clínica | diz quem resolve, sem contar nada sobre o que existe |
| api, entrar com e-mail ou senha errados (`401`) | E-mail ou senha inválidos | E-mail ou senha incorretos. Confira os dois e tente de novo | continua uma frase só para os dois casos, agora com o próximo passo |
| tela, sem conexão (`services/api.js`) | Não foi possível falar com o servidor. Confira sua internet e tente de novo. | Não foi possível conectar ao sistema. Confira sua internet e tente de novo. | a mesma ajuda, com a palavra que a pessoa conhece |
