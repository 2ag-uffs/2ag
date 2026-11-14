-- baixar um arquivo do prontuario vira operacao propria na trilha, uma linha por arquivo,
-- com o tipo do arquivo e a marca da pesquisa no detalhe (revisao das regras R19)
alter table audit_event drop constraint ck_audit_event_operation;
alter table audit_event add constraint ck_audit_event_operation
    check (operation in ('CRIACAO', 'ALTERACAO', 'VISUALIZACAO', 'ANULACAO', 'ARQUIVAMENTO', 'REATIVACAO',
        'REDEFINICAO_DE_SENHA', 'DESATIVACAO', 'EXPORTACAO'));
