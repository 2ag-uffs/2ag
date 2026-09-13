-- o item 5J do psqi pergunta "outras razoes, por favor descreva" e
-- depois "com que frequencia isso atrapalhou seu sono".
--
-- o campo de texto existia, a frequencia n. sem ela o componente de
-- disturbios do sono soma 8 itens em vez de 9, e o indice global sai
-- errado quando o algoritmo de 7 componentes for implementado (RF23).

alter table pittsburgh_scale add column freq_other_reason integer;
