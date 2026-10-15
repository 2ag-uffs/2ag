-- reforco no banco do q o codigo ja garante com trava de linha (issue 78)
-- so no postgres pq o h2 dos testes n aceita indice parcial

-- uma resposta valendo por paciente, escala e dia de inicio. o meem eh por consulta
create unique index uq_scale_response_day on scale_response (patient_id, scale_type, period_start)
    where annulled_at is null and scale_type <> 'MINI_EXAME_ESTADO_MENTAL';

create unique index uq_scale_response_meem on scale_response (appointment_id)
    where annulled_at is null and scale_type = 'MINI_EXAME_ESTADO_MENTAL';

create unique index uq_treatment_protocol_active on treatment_protocol (patient_id) where active;
