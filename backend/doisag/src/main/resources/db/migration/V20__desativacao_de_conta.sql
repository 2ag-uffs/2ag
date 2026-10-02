-- a administracao desativa e reativa conta de paciente (issue 62)
-- e a trilha passa a guardar quem ligou ou desligou uma conta

alter table audit_event drop constraint ck_audit_event_operation;
alter table audit_event add constraint ck_audit_event_operation
    check (operation in ('CRIACAO', 'ALTERACAO', 'VISUALIZACAO', 'ANULACAO', 'ARQUIVAMENTO', 'REATIVACAO',
        'REDEFINICAO_DE_SENHA', 'DESATIVACAO'));

alter table audit_event drop constraint ck_audit_event_record_type;
alter table audit_event add constraint ck_audit_event_record_type
    check (record_type in ('PRONTUARIO', 'CONSULTA', 'PRESCRICAO', 'MINI_EXAME', 'ANAMNESE',
        'ACOMPANHAMENTO_SEMANAL', 'ESCALA_HAMILTON', 'ESCALA_PITTSBURGH', 'REGISTRO_DOR', 'REGISTRO_SONO',
        'REGISTRO_TEA', 'DESIGNACAO_DE_ESCALA', 'ACOMPANHAMENTO_AUTOMATICO', 'CONTA_DE_PRESCRITOR',
        'CONTA_DE_PACIENTE'));
