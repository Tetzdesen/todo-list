package com.exemplo.todo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.exemplo.todo.model.Todo;

@DataJpaTest
class TodoRepositoryTest {

	@Autowired
	private TodoRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	private Todo antiga;
	private Todo intermediaria;
	private Todo recente;

	@BeforeEach
	void setUp() {
		antiga = salvar("Antiga", false);
		intermediaria = salvar("Intermediária", true);
		recente = salvar("Recente", false);

		// createdAt é preenchido pelo Hibernate; ajustamos via JPQL para ter datas distintas e previsíveis
		Instant base = Instant.parse("2026-01-01T10:00:00Z");
		definirCreatedAt(antiga, base);
		definirCreatedAt(intermediaria, base.plus(1, ChronoUnit.HOURS));
		definirCreatedAt(recente, base.plus(2, ChronoUnit.HOURS));
		entityManager.clear();
	}

	@Test
	void deveListarTodasDaMaisRecenteParaAMaisAntiga() {
		List<Todo> todos = repository.findAllByOrderByCreatedAtDesc();

		assertThat(todos).extracting(Todo::getTitle)
				.containsExactly("Recente", "Intermediária", "Antiga");
	}

	@Test
	void deveFiltrarPendentesMantendoAOrdem() {
		List<Todo> pendentes = repository.findByCompletedOrderByCreatedAtDesc(false);

		assertThat(pendentes).extracting(Todo::getTitle).containsExactly("Recente", "Antiga");
		assertThat(pendentes).allMatch(todo -> !todo.isCompleted());
	}

	@Test
	void deveFiltrarConcluidas() {
		List<Todo> concluidas = repository.findByCompletedOrderByCreatedAtDesc(true);

		assertThat(concluidas).extracting(Todo::getTitle).containsExactly("Intermediária");
	}

	@Test
	void deveRetornarListaVaziaQuandoNaoHaTarefas() {
		repository.deleteAll();

		assertThat(repository.findAllByOrderByCreatedAtDesc()).isEmpty();
	}

	private Todo salvar(String title, boolean completed) {
		Todo todo = new Todo(title, null);
		todo.setCompleted(completed);
		return entityManager.persistAndFlush(todo);
	}

	private void definirCreatedAt(Todo todo, Instant createdAt) {
		entityManager.getEntityManager()
				.createQuery("UPDATE Todo t SET t.createdAt = :createdAt WHERE t.id = :id")
				.setParameter("createdAt", createdAt)
				.setParameter("id", todo.getId())
				.executeUpdate();
	}

}
