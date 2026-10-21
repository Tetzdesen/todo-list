package com.exemplo.todo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.exemplo.todo.model.User;
import com.exemplo.todo.repository.UserRepository;
import com.exemplo.todo.service.UserService;

@DataJpaTest
@Import({ UserService.class, GoogleOidcUserService.class, BCryptPasswordEncoder.class })
class GoogleOidcUserServiceTest {

	@Autowired
	private GoogleOidcUserService service;

	@Autowired
	private UserRepository repository;

	@Test
	void deveCriarContaSemSenhaNoPrimeiroLogin() {
		AppUserPrincipal principal = service.toAppUser(google("sub-bia", "Bia@Gmail.com", true, "Bia Souza"));

		User bia = repository.findById(principal.getId()).orElseThrow();
		assertThat(bia.getName()).isEqualTo("Bia Souza");
		assertThat(bia.getEmail()).isEqualTo("bia@gmail.com");
		assertThat(bia.getGoogleId()).isEqualTo("sub-bia");
		assertThat(bia.getPasswordHash()).isNull();
		assertThat(principal.getSubject()).isEqualTo("sub-bia");
	}

	@Test
	void deveUsarAContaJaVinculadaAoGoogle() {
		User bia = new User("Bia", "bia@gmail.com");
		bia.setGoogleId("sub-bia");
		repository.saveAndFlush(bia);

		AppUserPrincipal principal = service.toAppUser(google("sub-bia", "bia@gmail.com", true, "Outro Nome"));

		assertThat(principal.getId()).isEqualTo(bia.getId());
		assertThat(principal.getDisplayName()).isEqualTo("Bia");
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void deveVincularContaComSenhaPeloEmailVerificadoRemovendoASenha() {
		User ana = new User("Ana", "ana@exemplo.com");
		ana.setPasswordHash("hash");
		repository.saveAndFlush(ana);

		AppUserPrincipal principal = service.toAppUser(google("sub-ana", "ana@exemplo.com", true, "Ana G."));

		assertThat(principal.getId()).isEqualTo(ana.getId());
		User vinculada = repository.findById(ana.getId()).orElseThrow();
		assertThat(vinculada.getGoogleId()).isEqualTo("sub-ana");
		// quem cadastrou a senha pode não ser o dono do e-mail
		assertThat(vinculada.getPasswordHash()).isNull();
	}

	@Test
	void deveRecusarEmailNaoVerificado() {
		User ana = new User("Ana", "ana@exemplo.com");
		ana.setPasswordHash("hash");
		repository.saveAndFlush(ana);

		assertThatThrownBy(() -> service.toAppUser(google("sub-x", "ana@exemplo.com", false, "Intrusa")))
				.isInstanceOf(OAuth2AuthenticationException.class);
		assertThat(repository.findById(ana.getId()).orElseThrow().getGoogleId()).isNull();
	}

	@Test
	void deveRecusarEmailJaVinculadoAOutraContaGoogle() {
		User ana = new User("Ana", "ana@exemplo.com");
		ana.setGoogleId("sub-ana");
		repository.saveAndFlush(ana);

		assertThatThrownBy(() -> service.toAppUser(google("sub-outro", "ana@exemplo.com", true, "Ana")))
				.isInstanceOf(OAuth2AuthenticationException.class);
	}

	private static OidcUser google(String subject, String email, boolean emailVerified, String name) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("sub", subject);
		claims.put("email", email);
		claims.put("email_verified", emailVerified);
		claims.put("name", name);
		OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), claims);
		return new DefaultOidcUser(List.of(), token);
	}

}
