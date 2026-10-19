package com.exemplo.todo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.exemplo.todo.model.Todo;

public interface TodoRepository extends JpaRepository<Todo, Long> {

	List<Todo> findAllByOrderByCreatedAtDesc();

	List<Todo> findByCompletedOrderByCreatedAtDesc(boolean completed);

}
