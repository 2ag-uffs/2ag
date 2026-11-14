-- o cancelamento e a remarcacao da consulta q o paciente ja recebeu por e-mail vao por e-mail tbm
-- (decisao de 14/11/2026, revisao das regras R11)
-- a consulta guarda o horario q o ultimo e-mail dela disse pro paciente. vazio enquanto nenhum e-mail saiu

alter table appointment add column emailed_date_time timestamp(6);
