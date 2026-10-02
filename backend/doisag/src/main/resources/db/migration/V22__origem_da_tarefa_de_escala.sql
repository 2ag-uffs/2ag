-- a tarefa guarda se veio do acompanhamento automatico (issue 70)
-- so ela marca o ciclo de 90 dias, entao a escala avulsa n mexe na proxima rodada (RF32)

alter table scale_task add column from_protocol boolean not null default false;

-- o job designa sem ninguem logado, entao a criacao da tarefa dele ficou na trilha sem autor
update scale_task set from_protocol = true
where id in (
    select record_id from audit_event
    where record_type = 'DESIGNACAO_DE_ESCALA' and operation = 'CRIACAO' and actor_id is null
);
