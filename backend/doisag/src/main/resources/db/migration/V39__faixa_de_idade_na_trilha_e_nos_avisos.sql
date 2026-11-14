-- a troca da data de nascimento entra na trilha com a data antiga e a nova (revisao das regras R9)
-- o detalhe eh um texto curto do evento e fica nulo nos eventos de antes
alter table audit_event add column detail varchar(255);

-- dia em q a prescritora soube q o paciente virou adulto, pelo aniversario de 18 anos ou pela data trocada
-- o job do dia so avisa uma vez por aniversario
alter table patient add column adult_notice_on date;
