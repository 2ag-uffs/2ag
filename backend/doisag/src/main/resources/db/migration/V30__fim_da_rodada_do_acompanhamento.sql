-- cada escala do acompanhamento guarda o fim do prazo da ultima rodada q ele mandou (RF32)
-- antes a proxima rodada olhava a ultima tarefa do acompanhamento de qualquer protocolo, e o
-- protocolo q substitui outro ficava preso na rodada mensal do anterior ate ela vencer

alter table protocol_item add column last_round_end date;

-- os acompanhamentos em andamento seguem de onde estavam: a rodada mais nova daquela escala segura a proxima
-- a q o arquivamento fechou sem resposta n segura, como antes
update protocol_item set last_round_end = (
    select max(scale_task.period_end)
    from scale_task, treatment_protocol
    where treatment_protocol.id = protocol_item.protocol_id
      and scale_task.patient_id = treatment_protocol.patient_id
      and scale_task.scale_type = protocol_item.scale_type
      and scale_task.from_protocol = true
      and scale_task.status <> 'NAO_RESPONDIDA'
)
where protocol_id in (select id from treatment_protocol where active = true);
