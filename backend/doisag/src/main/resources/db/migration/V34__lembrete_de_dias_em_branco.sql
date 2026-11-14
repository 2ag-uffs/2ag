-- no diario, dois dias seguidos em branco viram um lembrete, um so por sequencia (decisao de 14/11/2026, revisao das regras R6)
-- a tarefa guarda o ultimo dia em branco da sequencia q ja foi cobrada, entao o job do dia seguinte n cobra de novo

alter table scale_task add column blank_days_reminded_until date;

-- o dia do diario preenchido dps de o job fechar a semana fica com ela como atraso: n deixa a semana no prazo
-- nem segura ela fechada qnd o dia no prazo eh anulado
alter table scale_response add column filled_late boolean not null default false;
