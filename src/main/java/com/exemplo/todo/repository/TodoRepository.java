package com.exemplo.todo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.exemplo.todo.model.Todo;

// todas as consultas recebem o dono: uma tarefa de outro usuário simplesmente não é encontrada
public interface TodoRepository extends JpaRepository<Todo, Long> {

	List<Todo> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

	List<Todo> findByOwnerIdAndCompletedOrderByCreatedAtDesc(Long ownerId, boolean completed);

	Optional<Todo> findByIdAndOwnerId(Long id, Long ownerId);

}
