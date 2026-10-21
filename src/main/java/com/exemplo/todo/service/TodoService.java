package com.exemplo.todo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.exemplo.todo.dto.TodoCreateRequest;
import com.exemplo.todo.dto.TodoResponse;
import com.exemplo.todo.dto.TodoUpdateRequest;
import com.exemplo.todo.exception.TodoNotFoundException;
import com.exemplo.todo.model.Todo;
import com.exemplo.todo.repository.TodoRepository;
import com.exemplo.todo.repository.UserRepository;

// toda operação recebe o id do usuário logado; tarefas de outros usuários se comportam como inexistentes
@Service
@Transactional(readOnly = true)
public class TodoService {

	private final TodoRepository repository;
	private final UserRepository userRepository;

	public TodoService(TodoRepository repository, UserRepository userRepository) {
		this.repository = repository;
		this.userRepository = userRepository;
	}

	public List<TodoResponse> list(Long userId, Boolean completed) {
		List<Todo> todos = completed == null
				? repository.findByOwnerIdOrderByCreatedAtDesc(userId)
				: repository.findByOwnerIdAndCompletedOrderByCreatedAtDesc(userId, completed);
		return todos.stream().map(TodoResponse::from).toList();
	}

	public TodoResponse findById(Long userId, Long id) {
		return TodoResponse.from(getTodo(userId, id));
	}

	@Transactional
	public TodoResponse create(Long userId, TodoCreateRequest request) {
		Todo todo = new Todo(request.title(), request.description(), userRepository.getReferenceById(userId));
		return TodoResponse.from(repository.saveAndFlush(todo));
	}

	@Transactional
	public TodoResponse update(Long userId, Long id, TodoUpdateRequest request) {
		Todo todo = getTodo(userId, id);
		todo.setTitle(request.title());
		todo.setDescription(request.description());
		if (request.completed() != null) {
			todo.setCompleted(request.completed());
		}
		// flush para o @UpdateTimestamp ser aplicado antes de montar a resposta
		return TodoResponse.from(repository.saveAndFlush(todo));
	}

	@Transactional
	public TodoResponse toggle(Long userId, Long id) {
		Todo todo = getTodo(userId, id);
		todo.setCompleted(!todo.isCompleted());
		return TodoResponse.from(repository.saveAndFlush(todo));
	}

	@Transactional
	public void delete(Long userId, Long id) {
		repository.delete(getTodo(userId, id));
	}

	private Todo getTodo(Long userId, Long id) {
		return repository.findByIdAndOwnerId(id, userId).orElseThrow(() -> new TodoNotFoundException(id));
	}

}
