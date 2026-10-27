-- Tarefas criadas antes das contas de usuário ficaram sem dono e não aparecem para ninguém: são apagadas
-- para a coluna passar a ser obrigatória.
delete from todos where owner_id is null;

alter table todos alter column owner_id set not null;

-- todas as consultas de tarefas filtram pelo dono e ordenam pela data de criação
create index idx_todos_owner_created_at on todos (owner_id, created_at);
