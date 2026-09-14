-- lembretes automaticos (RF34)
-- a consulta e a tarefa guardam quando o lembrete saiu, entao o job do dia n manda o mesmo aviso duas vezes

alter table appointment add column reminder_sent_at timestamp(6);
alter table scale_task add column reminder_sent_at timestamp(6);
