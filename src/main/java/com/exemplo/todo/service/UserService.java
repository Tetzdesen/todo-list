package com.exemplo.todo.service;

import java.nio.charset.StandardCharsets;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.exemplo.todo.dto.RegisterRequest;
import com.exemplo.todo.exception.AccountNotFoundException;
import com.exemplo.todo.exception.EmailAlreadyUsedException;
import com.exemplo.todo.exception.GoogleLoginException;
import com.exemplo.todo.exception.InvalidFieldException;
import com.exemplo.todo.model.User;
import com.exemplo.todo.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {

	// o BCrypt recusa senhas com mais de 72 bytes; com acentos, 72 caracteres podem passar disso
	public static final int MAX_PASSWORD_BYTES = 72;

	private final UserRepository repository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public User register(RegisterRequest request) {
		if (exceedsPasswordLimit(request.password())) {
			throw new InvalidFieldException("password", "a senha deve ter no máximo 72 bytes (letras acentuadas contam 2)");
		}
		User user = new User(request.name().strip(), request.email());
		if (repository.existsByEmail(user.getEmail())) {
			throw new EmailAlreadyUsedException();
		}
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		try {
			return repository.saveAndFlush(user);
		} catch (DataIntegrityViolationException ex) {
			// outro cadastro com o mesmo e-mail entrou entre a verificação e a gravação
			throw new EmailAlreadyUsedException();
		}
	}

	/**
	 * Conta usada no login com Google: a já vinculada ao {@code googleId}; senão, a conta com o mesmo e-mail
	 * (vinculando-a e removendo a senha); senão, uma conta nova sem senha. Sem e-mail verificado, só a conta já
	 * vinculada é aceita.
	 */
	@Transactional
	public User findOrCreateFromGoogle(String googleId, String email, boolean emailVerified, String name) {
		var linked = repository.findByGoogleId(googleId);
		if (linked.isPresent()) {
			return linked.get();
		}
		if (!emailVerified || email == null) {
			throw new GoogleLoginException("O e-mail da conta Google não está verificado");
		}
		User user = repository.findByEmail(User.normalizeEmail(email))
				.orElseGet(() -> new User(displayName(name, email), email));
		if (user.getGoogleId() != null) {
			throw new GoogleLoginException("Este e-mail já está vinculado a outra conta Google");
		}
		// o cadastro com senha não confirma o e-mail: quem criou a conta pode não ser o dono dele. O Google
		// acabou de confirmar o dono, então a senha antiga deixa de valer (a pessoa entra só pelo Google)
		user.setPasswordHash(null);
		user.setGoogleId(googleId);
		return repository.saveAndFlush(user);
	}

	private static String displayName(String name, String email) {
		String value = name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name.strip();
		return value.length() > 100 ? value.substring(0, 100) : value;
	}

	public User getById(Long id) {
		return repository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
	}

	public static boolean exceedsPasswordLimit(String password) {
		return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
	}

}
