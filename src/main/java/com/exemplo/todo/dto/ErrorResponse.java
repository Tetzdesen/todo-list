package com.exemplo.todo.dto;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
		Instant timestamp,
		int status,
		String error,
		String message,
		String path,
		List<FieldError> fieldErrors) {

	public static ErrorResponse of(HttpStatus status, String message, String path, List<FieldError> fieldErrors) {
		return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors);
	}

}
