-- o job diario guarda o ultimo dia em q terminou todas as etapas (issue 93)
-- assim a api q estava fora do ar as 8h sabe q precisa rodar qnd volta
-- a tabela tem uma linha so, criada na primeira vez q o job termina

create table daily_cycle (
    id bigint not null,
    last_completed_date date not null,
    primary key (id)
);
