package com.exemplo.todo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.context.ActiveProfiles;

// o perfil prod liga as sessões no banco; o H2 faz o papel do PostgreSQL só para a aplicação subir
@SpringBootTest(properties = { "DATABASE_URL=jdbc:h2:mem:prodprofile", "DB_USER=sa", "DB_PASSWORD=" })
@ActiveProfiles("prod")
class ProdProfileTest {

	@Autowired
	private ApplicationContext context;

	@Test
	void deveGuardarAsSessoesNoBancoEmProducao() {
		assertThat(context.getBeansOfType(SessionRepositoryFilter.class)).isNotEmpty();
	}

}
