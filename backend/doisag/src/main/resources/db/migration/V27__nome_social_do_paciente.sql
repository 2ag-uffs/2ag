-- nome social do paciente, opcional (issue 109 item 5, RN15)
-- nulo qnd a pessoa n informou e ai as telas usam o nome civil
alter table patient add column social_name varchar(255);
