package com.exemplo.todo.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.exemplo.todo.model.Todo;

// todas as consultas recebem o dono: uma tarefa de outro usuário simplesmente não é encontrada
public interface TodoRepository extends JpaRepository<Todo, Long> {

	Page<Todo> findByOwnerId(Long ownerId, Pageable pageable);

	Page<Todo> findByOwnerIdAndCompleted(Long ownerId, boolean completed, Pageable pageable);

	Optional<Todo> findByIdAndOwnerId(Long id, Long ownerId);

}
