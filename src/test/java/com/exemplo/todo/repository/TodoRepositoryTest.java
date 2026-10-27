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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.exemplo.todo.model.Todo;
import com.exemplo.todo.model.User;

@DataJpaTest
class TodoRepositoryTest {

	private static final PageRequest RECENTES = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

	@Autowired
	private TodoRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	private User ana;
	private User bruno;
	private Todo antiga;
	private Todo intermediaria;
	private Todo recente;
	private Todo deBruno;

	@BeforeEach
	void setUp() {
		ana = entityManager.persistAndFlush(new User("Ana", "ana@exemplo.com"));
		bruno = entityManager.persistAndFlush(new User("Bruno", "bruno@exemplo.com"));

		antiga = salvar("Antiga", false);
		intermediaria = salvar("Intermediária", true);
		recente = salvar("Recente", false);
		deBruno = salvar("De Bruno", false, bruno);

		// createdAt é preenchido pelo Hibernate; ajustamos via JPQL para ter datas distintas e previsíveis
		Instant base = Instant.parse("2026-01-01T10:00:00Z");
		definirCreatedAt(antiga, base);
		definirCreatedAt(intermediaria, base.plus(1, ChronoUnit.HOURS));
		definirCreatedAt(recente, base.plus(2, ChronoUnit.HOURS));
		definirCreatedAt(deBruno, base.plus(3, ChronoUnit.HOURS));
		entityManager.clear();
	}

	@Test
	void deveListarTodasDaMaisRecenteParaAMaisAntiga() {
		Page<Todo> todos = repository.findByOwnerId(ana.getId(), RECENTES);

		assertThat(todos.getTotalElements()).isEqualTo(3);
		assertThat(todos).extracting(Todo::getTitle)
				.containsExactly("Recente", "Intermediária", "Antiga");
	}

	@Test
	void deveFiltrarPendentesMantendoAOrdem() {
		List<Todo> pendentes = repository.findByOwnerIdAndCompleted(ana.getId(), false, RECENTES).getContent();

		assertThat(pendentes).extracting(Todo::getTitle).containsExactly("Recente", "Antiga");
		assertThat(pendentes).allMatch(todo -> !todo.isCompleted());
	}

	@Test
	void deveFiltrarConcluidas() {
		List<Todo> concluidas = repository.findByOwnerIdAndCompleted(ana.getId(), true, RECENTES).getContent();

		assertThat(concluidas).extracting(Todo::getTitle).containsExactly("Intermediária");
	}

	@Test
	void deveListarSoAsTarefasDoDono() {
		assertThat(repository.findByOwnerId(bruno.getId(), RECENTES))
				.extracting(Todo::getTitle).containsExactly("De Bruno");
	}

	@Test
	void devePaginarMantendoAOrdem() {
		Page<Todo> segunda = repository.findByOwnerId(ana.getId(),
				PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

		assertThat(segunda).extracting(Todo::getTitle).containsExactly("Antiga");
		assertThat(segunda.getTotalElements()).isEqualTo(3);
		assertThat(segunda.getTotalPages()).isEqualTo(2);
	}

	@Test
	void naoDeveEncontrarTarefaDeOutroDono() {
		assertThat(repository.findByIdAndOwnerId(deBruno.getId(), ana.getId())).isEmpty();
		assertThat(repository.findByIdAndOwnerId(deBruno.getId(), bruno.getId())).isPresent();
	}

	@Test
	void deveRetornarListaVaziaQuandoNaoHaTarefas() {
		repository.deleteAll();

		assertThat(repository.findByOwnerId(ana.getId(), RECENTES)).isEmpty();
	}

	private Todo salvar(String title, boolean completed) {
		return salvar(title, completed, ana);
	}

	private Todo salvar(String title, boolean completed, User owner) {
		Todo todo = new Todo(title, null, owner);
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
