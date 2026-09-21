-- indice nas chave estrangeira q ficaram sem, seguindo a regra escrita na V1 (issue 106)
--
-- o de scale_response (appointment_id) eh o unico q muda tempo de consulta hoje:
-- eh por ele q a consulta acha o mini exame aplicado nela, tanto pra abrir a tela
-- no resultado quanto pra barrar a anulacao
--
-- os outros sao alinhamento: chave de anulacao e de arquivamento so sao lidas
-- na trilha de auditoria, mas sem indice o banco varre a tabela inteira

create index idx_appointment_annulled_by on appointment (annulled_by_id);
create index idx_prescription_annulled_by on prescription (annulled_by_id);
create index idx_anamnesis_annulled_by on anamnesis (annulled_by_id);
create index idx_patient_archived_by on patient (archived_by_id);

create index idx_scale_response_appointment on scale_response (appointment_id);
create index idx_scale_response_prescriber on scale_response (prescriber_id);
create index idx_scale_response_reviewed_by on scale_response (reviewed_by_id);
create index idx_scale_response_annulled_by on scale_response (annulled_by_id);

-- sobre os dois check de tipo de escala da V13, q parecem divergente e n sao:
--
-- ck_scale_task_type n aceita MINI_EXAME_ESTADO_MENTAL de proposito, pq o mini
-- exame eh aplicado pelo prescritor na consulta e nunca vira tarefa do paciente (RN09)
--
-- ck_scale_response_type n aceita ANAMNESE de proposito, pq a anamnese tem tabela
-- propria e nunca cai em scale_response
--
-- entao os dois estao certos e ficam como estao
