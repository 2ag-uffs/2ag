-- o pedido de consulta de amanha ainda sem resposta avisa o paciente pra falar com a clinica
-- (decisao de 14/11/2026, revisao das regras R1)
-- o pedido guarda quando o aviso saiu, entao o job do dia n manda o mesmo aviso duas vezes

alter table appointment add column unanswered_notice_sent_at timestamp(6);
