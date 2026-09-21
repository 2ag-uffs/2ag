-- consulta em q o paciente n apareceu (issue 69)
--
-- antes so dava pra sair de AGENDADA digitando o registro clinico, entao a consulta
-- em q ninguem apareceu ficava marcada pra sempre e a agenda mentia

alter table appointment drop constraint ck_appointment_status;
alter table appointment add constraint ck_appointment_status
    check (status in ('SOLICITADA', 'AGENDADA', 'EM_ANDAMENTO', 'CONCLUIDA', 'CANCELADA', 'RECUSADA',
        'NAO_COMPARECEU'));
