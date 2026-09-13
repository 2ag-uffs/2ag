-- registro clinico da consulta (RF04)
-- consulta registrada por engano eh anulada com motivo e continua no historico
-- a trilha de auditoria ganha a operacao de anulacao

alter table appointment add column annulled_at timestamp(6);
alter table appointment add column annulled_by_id bigint;
alter table appointment add column annulment_reason TEXT;
alter table appointment add constraint fk_appointment_annulled_by foreign key (annulled_by_id) references users (id);

alter table audit_event drop constraint ck_audit_event_operation;
alter table audit_event add constraint ck_audit_event_operation
    check (operation in ('CRIACAO', 'ALTERACAO', 'VISUALIZACAO', 'ANULACAO'));
