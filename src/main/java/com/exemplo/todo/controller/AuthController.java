package com.exemplo.todo.controller;

import java.net.URI;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.exemplo.todo.dto.LoginRequest;
import com.exemplo.todo.dto.RegisterRequest;
import com.exemplo.todo.dto.UserResponse;
import com.exemplo.todo.model.User;
import com.exemplo.todo.security.AppUserPrincipal;
import com.exemplo.todo.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Cadastro, login e dados da sessão. O logout ({@code POST /api/auth/logout}) é feito pelo filtro de logout do
 * Spring Security, configurado em {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserService userService;
	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final CsrfTokenRepository csrfTokenRepository;
	private final ObjectProvider<ClientRegistrationRepository> googleRegistrations;

	public AuthController(UserService userService, AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository,
			ObjectProvider<ClientRegistrationRepository> googleRegistrations) {
		this.userService = userService;
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
		this.googleRegistrations = googleRegistrations;
	}

	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest body,
			HttpServletRequest request, HttpServletResponse response) {
		User user = userService.register(body);
		AppUserPrincipal principal = AppUserPrincipal.from(user);
		principal.eraseCredentials();
		startSession(principal, request, response);
		return ResponseEntity.created(URI.create("/api/auth/me")).body(UserResponse.from(user));
	}

	@PostMapping("/login")
	public UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		if (UserService.exceedsPasswordLimit(body.password())) {
			throw new BadCredentialsException("senha maior que o limite do BCrypt");
		}
		var authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
		AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
		startSession(principal, request, response);
		return UserResponse.from(userService.getById(principal.getId()));
	}

	// pedir o CsrfToken como parâmetro faz o cookie XSRF-TOKEN ser gravado na resposta
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal, CsrfToken csrfToken) {
		csrfToken.getToken();
		return UserResponse.from(userService.getById(principal.getId()));
	}

	@GetMapping("/providers")
	public Map<String, Boolean> providers(CsrfToken csrfToken) {
		csrfToken.getToken();
		return Map.of("google", googleRegistrations.getIfAvailable() != null);
	}

	private void startSession(AppUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
		// troca o id da sessão para evitar fixação de sessão
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);

		// token CSRF novo a cada login
		csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);
	}

}
