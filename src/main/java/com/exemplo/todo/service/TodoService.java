package com.exemplo.todo.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.exemplo.todo.dto.PageResponse;
import com.exemplo.todo.dto.TodoCreateRequest;
import com.exemplo.todo.dto.TodoPatchRequest;
import com.exemplo.todo.dto.TodoResponse;
import com.exemplo.todo.exception.InvalidFieldException;
import com.exemplo.todo.exception.TodoConflictException;
import com.exemplo.todo.exception.TodoNotFoundException;
import com.exemplo.todo.model.Todo;
import com.exemplo.todo.repository.TodoRepository;
import com.exemplo.todo.repository.UserRepository;

// toda operação recebe o id do usuário logado; tarefas de outros usuários se comportam como inexistentes
@Service
@Transactional(readOnly = true)
public class TodoService {

	// o id desempata tarefas criadas no mesmo instante, para nenhuma se repetir ou sumir entre as páginas
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final TodoRepository repository;
	private final UserRepository userRepository;

	public TodoService(TodoRepository repository, UserRepository userRepository) {
		this.repository = repository;
		this.userRepository = userRepository;
	}

	public PageResponse<TodoResponse> list(Long userId, Boolean completed, int page, int size) {
		PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
		Page<Todo> todos = completed == null
				? repository.findByOwnerId(userId, pageable)
				: repository.findByOwnerIdAndCompleted(userId, completed, pageable);
		return PageResponse.from(todos.map(TodoResponse::from));
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
	public TodoResponse update(Long userId, Long id, TodoPatchRequest request) {
		// título e conclusão não podem ser apagados; a descrição pode
		if (request.hasTitle() && request.getTitle() == null) {
			throw new InvalidFieldException("title", "não deve ser nulo");
		}
		if (request.hasCompleted() && request.getCompleted() == null) {
			throw new InvalidFieldException("completed", "não deve ser nulo");
		}
		Todo todo = getTodo(userId, id);
		if (request.getVersion() != null && !request.getVersion().equals(todo.getVersion())) {
			throw new TodoConflictException(id);
		}
		if (request.hasTitle()) {
			todo.setTitle(request.getTitle());
		}
		if (request.hasDescription()) {
			todo.setDescription(request.getDescription());
		}
		if (request.hasCompleted()) {
			todo.setCompleted(request.getCompleted());
		}
		// flush para o @UpdateTimestamp e a nova versão serem aplicados antes de montar a resposta
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
