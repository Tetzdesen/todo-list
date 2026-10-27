-- controle de concorrência otimista: cada alteração incrementa a versão (@Version em Todo)
alter table todos add column version bigint default 0 not null;
