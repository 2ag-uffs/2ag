-- anamnese (RF19)
-- anamnese registrada por engano eh anulada pelo prescritor com motivo e continua no historico

alter table anamnesis add column annulled_at timestamp(6);
alter table anamnesis add column annulled_by_id bigint;
alter table anamnesis add column annulment_reason TEXT;
alter table anamnesis add constraint fk_anamnesis_annulled_by foreign key (annulled_by_id) references users (id);
