package com.exemplo.todo.exception;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.exemplo.todo.dto.ErrorResponse;
import com.exemplo.todo.dto.FieldError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(TodoNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(TodoNotFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
	}

	@ExceptionHandler(TodoConflictException.class)
	public ResponseEntity<ErrorResponse> handleConflict(TodoConflictException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
	}

	// duas alterações da mesma tarefa ao mesmo tempo: a segunda a gravar perde
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
			HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, "A tarefa foi alterada em outro lugar. A lista foi recarregada; tente de novo.",
				request, null);
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

	// a conta foi apagada com a sessão ainda aberta: a sessão deixa de valer e o cliente volta para o login
	@ExceptionHandler(AccountNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex,
			HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		return build(HttpStatus.UNAUTHORIZED, "Sessão inválida; entre novamente", request, null);
	}

	@ExceptionHandler(TooManyLoginAttemptsException.class)
	public ResponseEntity<ErrorResponse> handleTooManyLoginAttempts(TooManyLoginAttemptsException ex,
			HttpServletRequest request) {
		// arredonda para cima: o cliente nunca é mandado tentar de novo antes do desbloqueio
		long seconds = Math.max(1, (ex.getRetryAfter().toMillis() + 999) / 1000);
		long minutes = (seconds + 59) / 60;
		String message = "Muitas tentativas de login. Tente de novo em " + minutes
				+ (minutes == 1 ? " minuto." : " minutos.");
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
				.body(ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS, message, request.getRequestURI(), null));
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

	// restrições em parâmetros de query, como page e size na listagem de tarefas
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleParameterValidation(HandlerMethodValidationException ex,
			HttpServletRequest request) {
		List<FieldError> fieldErrors = ex.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> new FieldError(result.getMethodParameter().getParameterName(),
								error.getDefaultMessage())))
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

	// demais exceções do Spring MVC (parâmetro obrigatório ausente, 406...) já trazem o status certo; o resto é 500,
	// sem detalhes internos na resposta
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		if (ex instanceof org.springframework.web.ErrorResponse springError) {
			HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
			return build(status, status.getReasonPhrase(), request, null);
		}
		log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor", request, null);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request,
			List<FieldError> fieldErrors) {
		return ResponseEntity.status(status)
				.body(ErrorResponse.of(status, message, request.getRequestURI(), fieldErrors));
	}

}
