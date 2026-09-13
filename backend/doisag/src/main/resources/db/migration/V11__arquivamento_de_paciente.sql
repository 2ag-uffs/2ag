-- arquivamento de paciente
-- o paciente arquivado sai da lista de ativos e o acompanhamento automatico dele acaba
-- o prontuario continua inteiro e o prescritor pode reativar quando quiser

alter table patient add column archived_at timestamp(6);
alter table patient add column archived_by_id bigint;
alter table patient add constraint fk_patient_archived_by foreign key (archived_by_id) references users (id);

-- arquivar e reativar entram na trilha de auditoria
alter table audit_event drop constraint ck_audit_event_operation;
alter table audit_event add constraint ck_audit_event_operation
    check (operation in ('CRIACAO', 'ALTERACAO', 'VISUALIZACAO', 'ANULACAO', 'ARQUIVAMENTO', 'REATIVACAO'));
