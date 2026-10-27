package com.exemplo.todo.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import com.exemplo.todo.dto.PageResponse;
import com.exemplo.todo.dto.TodoCreateRequest;
import com.exemplo.todo.dto.TodoResponse;
import com.exemplo.todo.dto.TodoUpdateRequest;
import com.exemplo.todo.security.AppUserPrincipal;
import com.exemplo.todo.service.TodoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

	private static final int MAX_PAGE_SIZE = 100;

	private final TodoService service;

	public TodoController(TodoService service) {
		this.service = service;
	}

	@GetMapping
	public PageResponse<TodoResponse> list(@AuthenticationPrincipal AppUserPrincipal user,
			@RequestParam(required = false) Boolean completed,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
		return service.list(user.getId(), completed, page, size);
	}

	@GetMapping("/{id}")
	public TodoResponse findById(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long id) {
		return service.findById(user.getId(), id);
	}

	@PostMapping
	public ResponseEntity<TodoResponse> create(@AuthenticationPrincipal AppUserPrincipal user,
			@Valid @RequestBody TodoCreateRequest request) {
		TodoResponse created = service.create(user.getId(), request);
		return ResponseEntity.created(URI.create("/api/todos/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	public TodoResponse update(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long id,
			@Valid @RequestBody TodoUpdateRequest request) {
		return service.update(user.getId(), id, request);
	}

	@PatchMapping("/{id}/toggle")
	public TodoResponse toggle(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long id) {
		return service.toggle(user.getId(), id);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable Long id) {
		service.delete(user.getId(), id);
		return ResponseEntity.noContent().build();
	}

}
