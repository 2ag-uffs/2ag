-- a agenda do prescritor ja perguntava a duracao da consulta (30, 60 ou
-- 90 minutos), mas o modelo so guardava a hora de inicio, entao a
-- resposta era jogada fora.
--
-- sem isso a grade de horarios da agenda n tem como saber ate quando o
-- horario esta ocupado, e duas consultas podiam cair no mesmo lugar.

alter table appointment add column duration_minutes integer;

-- as consultas que ja existem ficam com 60, que eh o padrao da tela
update appointment set duration_minutes = 60 where duration_minutes is null;
