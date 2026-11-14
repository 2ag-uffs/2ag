-- a prescritora recebe um aviso 7 dias antes do fim do acompanhamento automatico e outro no dia do fim
-- (revisao das regras R5). o acompanhamento guarda quando cada aviso saiu, entao o job do dia n repete

alter table treatment_protocol add column ending_soon_notice_at timestamp(6);
alter table treatment_protocol add column last_day_notice_at timestamp(6);
