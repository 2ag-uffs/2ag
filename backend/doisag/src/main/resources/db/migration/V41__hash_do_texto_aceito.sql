-- cada aceite guarda o sha-256 do texto mostrado, ja com os dados da clinica no lugar dos colchetes
-- (revisao das regras R21). trocar uma variavel da clinica muda o texto sem mudar a versao, e o hash prova
-- qual texto a pessoa aceitou. os aceites de antes ficam sem, pq o texto deles n eh mais conhecido
alter table consent_acceptance add column term_hash varchar(64);
