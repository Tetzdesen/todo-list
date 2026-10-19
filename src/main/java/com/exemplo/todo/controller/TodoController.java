package com.exemplo.todo.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.exemplo.todo.dto.TodoCreateRequest;
import com.exemplo.todo.dto.TodoResponse;
import com.exemplo.todo.dto.TodoUpdateRequest;
import com.exemplo.todo.service.TodoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

	private final TodoService service;

	public TodoController(TodoService service) {
		this.service = service;
	}

	@GetMapping
	public List<TodoResponse> list(@RequestParam(required = false) Boolean completed) {
		return service.list(completed);
	}

	@GetMapping("/{id}")
	public TodoResponse findById(@PathVariable Long id) {
		return service.findById(id);
	}

	@PostMapping
	public ResponseEntity<TodoResponse> create(@Valid @RequestBody TodoCreateRequest request) {
		TodoResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/todos/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public TodoResponse update(@PathVariable Long id, @Valid @RequestBody TodoUpdateRequest request) {
		return service.update(id, request);
	}

	@PatchMapping("/{id}/toggle")
	public TodoResponse toggle(@PathVariable Long id) {
		return service.toggle(id);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}

}
