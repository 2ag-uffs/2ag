-- dois instrumentos estavam incompletos, e isso deslocava a leitura do
-- resultado:
--
-- o HAM-A tem 14 itens e ia so ate o 13, entao o escore maximo era 52
-- em vez de 56. as faixas do formulario da clinica (abaixo de 9, 9 a 15,
-- 16 a 25, acima de 26) supoem o instrumento inteiro.
--
-- o MEEM tem 30 pontos e ia so ate 27, faltando ler e executar "feche
-- os olhos", escrever uma frase e copiar os pentagonos. teto menor
-- desloca todas as faixas de interpretacao.

alter table hamilton_scale add column interview_behavior integer;

alter table mental_state_exam add column reading integer;
alter table mental_state_exam add column writing integer;
alter table mental_state_exam add column copying integer;
