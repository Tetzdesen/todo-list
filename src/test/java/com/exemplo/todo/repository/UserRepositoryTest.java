package com.exemplo.todo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.exemplo.todo.model.User;

@DataJpaTest
class UserRepositoryTest {

	@Autowired
	private UserRepository repository;

	@Test
	void deveGuardarEmailEmMinusculasESemEspacos() {
		repository.saveAndFlush(new User("Ana", "  Ana@Exemplo.COM "));

		assertThat(repository.findByEmail("ana@exemplo.com")).isPresent();
	}

	@Test
	void deveRejeitarEmailDuplicadoMesmoComMaiusculas() {
		repository.saveAndFlush(new User("Ana", "ana@exemplo.com"));

		assertThatThrownBy(() -> repository.saveAndFlush(new User("Outra Ana", "ANA@exemplo.com")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deveBuscarPeloIdDoGoogle() {
		User user = new User("Bia", "bia@gmail.com");
		user.setGoogleId("google-123");
		repository.saveAndFlush(user);

		assertThat(repository.findByGoogleId("google-123")).get()
				.extracting(User::getEmail).isEqualTo("bia@gmail.com");
	}

}
