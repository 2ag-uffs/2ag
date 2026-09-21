-- o administrador gera link de senha nova pra um prescritor (issue 60)
--
-- sem isso, prescritor q esquece a senha q o admin escolheu fica de fora pra sempre,
-- pq no piloto pode n ter smtp e a unica saida era update na mao no banco

-- evento de conta n fala de paciente nenhum, entao o paciente da trilha passa a aceitar nulo
-- as duas consultas da trilha continuam funcionando: a do prontuario filtra por paciente
-- e a da administracao lista tudo q n foi o paciente q fez
alter table audit_event alter column patient_id drop not null;

alter table audit_event drop constraint ck_audit_event_operation;
alter table audit_event add constraint ck_audit_event_operation
    check (operation in ('CRIACAO', 'ALTERACAO', 'VISUALIZACAO', 'ANULACAO', 'ARQUIVAMENTO', 'REATIVACAO',
        'REDEFINICAO_DE_SENHA'));

alter table audit_event drop constraint ck_audit_event_record_type;
alter table audit_event add constraint ck_audit_event_record_type
    check (record_type in ('PRONTUARIO', 'CONSULTA', 'PRESCRICAO', 'MINI_EXAME', 'ANAMNESE',
        'ACOMPANHAMENTO_SEMANAL', 'ESCALA_HAMILTON', 'ESCALA_PITTSBURGH', 'REGISTRO_DOR', 'REGISTRO_SONO',
        'REGISTRO_TEA', 'DESIGNACAO_DE_ESCALA', 'ACOMPANHAMENTO_AUTOMATICO', 'CONTA_DE_PRESCRITOR'));
