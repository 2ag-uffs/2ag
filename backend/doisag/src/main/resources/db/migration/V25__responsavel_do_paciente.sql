-- quem acompanha o paciente, tudo opcional e fora do csv anonimo (issue 125, RF38)
alter table patient add column guardian_name varchar(255);
alter table patient add column guardian_relationship varchar(60);
alter table patient add column guardian_phone varchar(11);
