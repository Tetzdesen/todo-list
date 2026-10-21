package com.exemplo.todo.exception;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.exemplo.todo.dto.ErrorResponse;
import com.exemplo.todo.dto.FieldError;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(TodoNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(TodoNotFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
	}

	@ExceptionHandler(EmailAlreadyUsedException.class)
	public ResponseEntity<ErrorResponse> handleEmailAlreadyUsed(EmailAlreadyUsedException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
	}

	// só o login autentica dentro de um controller; a mensagem não revela se o e-mail existe
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex,
			HttpServletRequest request) {
		return build(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos", request, null);
	}

	@ExceptionHandler(InvalidFieldException.class)
	public ResponseEntity<ErrorResponse> handleInvalidField(InvalidFieldException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Dados inválidos", request,
				List.of(new FieldError(ex.getField(), ex.getMessage())));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
				.toList();
		return build(HttpStatus.BAD_REQUEST, "Dados inválidos", request, fieldErrors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
			HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido ou malformado", request, null);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
			HttpServletRequest request) {
		String message = "Valor inválido para o parâmetro '" + ex.getName() + "': " + ex.getValue();
		return build(HttpStatus.BAD_REQUEST, message, request, null);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "Recurso não encontrado", request, null);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request) {
		String message = "Método " + ex.getMethod() + " não suportado para este caminho";
		ResponseEntity<ErrorResponse> response = build(HttpStatus.METHOD_NOT_ALLOWED, message, request, null);
		Set<HttpMethod> supported = ex.getSupportedHttpMethods();
		return supported == null ? response
				: ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).allow(supported.toArray(HttpMethod[]::new))
						.body(response.getBody());
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
			HttpServletRequest request) {
		return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type não suportado; use application/json", request,
				null);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request,
			List<FieldError> fieldErrors) {
		return ResponseEntity.status(status)
				.body(ErrorResponse.of(status, message, request.getRequestURI(), fieldErrors));
	}

}
