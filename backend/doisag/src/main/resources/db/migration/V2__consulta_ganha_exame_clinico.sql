-- a tela de consulta ja coletava exame fisico, pressao, peso, altura e
-- exames complementares, mas o modelo n tinha onde guardar, entao esses
-- dados eram perdidos no caminho.
--
-- peso e altura entram como numero e n como texto: sao os unicos dados
-- da consulta que fazem sentido acompanhar ao longo do tratamento.

alter table appointment add column physical_exam TEXT;
alter table appointment add column complementary_exams TEXT;
alter table appointment add column blood_pressure varchar(255);
alter table appointment add column weight float4;
alter table appointment add column height integer;
