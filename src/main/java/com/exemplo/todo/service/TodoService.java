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

@Service
@Transactional(readOnly = true)
public class TodoService {

	private final TodoRepository repository;

	public TodoService(TodoRepository repository) {
		this.repository = repository;
	}

	public List<TodoResponse> list(Boolean completed) {
		List<Todo> todos = completed == null
				? repository.findAllByOrderByCreatedAtDesc()
				: repository.findByCompletedOrderByCreatedAtDesc(completed);
		return todos.stream().map(TodoResponse::from).toList();
	}

	public TodoResponse findById(Long id) {
		return TodoResponse.from(getTodo(id));
	}

	@Transactional
	public TodoResponse create(TodoCreateRequest request) {
		Todo todo = new Todo(request.title(), request.description());
		return TodoResponse.from(repository.saveAndFlush(todo));
	}

	@Transactional
	public TodoResponse update(Long id, TodoUpdateRequest request) {
		Todo todo = getTodo(id);
		todo.setTitle(request.title());
		todo.setDescription(request.description());
		if (request.completed() != null) {
			todo.setCompleted(request.completed());
		}
		// flush para o @UpdateTimestamp ser aplicado antes de montar a resposta
		return TodoResponse.from(repository.saveAndFlush(todo));
	}

	@Transactional
	public TodoResponse toggle(Long id) {
		Todo todo = getTodo(id);
		todo.setCompleted(!todo.isCompleted());
		return TodoResponse.from(repository.saveAndFlush(todo));
	}

	@Transactional
	public void delete(Long id) {
		repository.delete(getTodo(id));
	}

	private Todo getTodo(Long id) {
		return repository.findById(id).orElseThrow(() -> new TodoNotFoundException(id));
	}

}
