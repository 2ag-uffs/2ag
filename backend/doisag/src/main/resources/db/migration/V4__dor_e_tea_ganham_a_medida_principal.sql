-- os dois formularios tinham a medida principal faltando no modelo:
--
-- o registro de dor tem uma escala visual de 0 a 10 no papel, que eh o
-- dado central dele, e n tinha coluna. so os 5 itens de interferencia
-- estavam sendo guardados.
--
-- o registro de TEA tem a autoavaliacao de qualidade de vida de 0 a 10,
-- mesma coisa.
--
-- sem esses dois campos a tela de progresso n teria o que plotar.

alter table pain_log add column pain_intensity integer;
alter table tealog add column quality_of_life integer;
