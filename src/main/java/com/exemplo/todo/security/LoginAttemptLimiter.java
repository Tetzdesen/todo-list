package com.exemplo.todo.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.exemplo.todo.exception.TooManyLoginAttemptsException;
import com.exemplo.todo.model.User;

/**
 * Limita as tentativas erradas de login por e-mail (contra quem tenta adivinhar a senha de uma conta) e por IP
 * (contra quem testa muitas contas). As contagens ficam na memória, como as sessões: valem para uma instância só e
 * zeram a cada reinício. E-mails inexistentes contam do mesmo jeito, então o bloqueio não revela se a conta existe.
 */
@Component
public class LoginAttemptLimiter {

	static final int MAX_FAILURES_PER_EMAIL = 5;
	static final int MAX_FAILURES_PER_IP = 20;
	static final Duration WINDOW = Duration.ofMinutes(15);

	// acima disso, as janelas já encerradas são removidas para o mapa não crescer sem limite
	private static final int PURGE_THRESHOLD = 10_000;

	private final Map<String, Failures> failures = new ConcurrentHashMap<>();
	private final Clock clock;

	@Autowired
	public LoginAttemptLimiter() {
		this(Clock.systemUTC());
	}

	LoginAttemptLimiter(Clock clock) {
		this.clock = clock;
	}

	// falhas contadas desde o primeiro erro até o fim da janela
	private record Failures(int count, Instant windowEnd) {
	}

	public void checkAllowed(String email, String ip) {
		Instant now = clock.instant();
		Instant blockedUntil = later(
				blockedUntil(emailKey(email), MAX_FAILURES_PER_EMAIL, now),
				blockedUntil(ipKey(ip), MAX_FAILURES_PER_IP, now));
		if (blockedUntil != null) {
			throw new TooManyLoginAttemptsException(Duration.between(now, blockedUntil));
		}
	}

	public void recordFailure(String email, String ip) {
		Instant now = clock.instant();
		increment(emailKey(email), now);
		increment(ipKey(ip), now);
		if (failures.size() > PURGE_THRESHOLD) {
			failures.values().removeIf(entry -> !now.isBefore(entry.windowEnd()));
		}
	}

	// só o e-mail é liberado: um login certo não deve zerar o IP de quem testa várias contas
	public void recordSuccess(String email) {
		failures.remove(emailKey(email));
	}

	private void increment(String key, Instant now) {
		failures.compute(key, (k, current) -> current == null || !now.isBefore(current.windowEnd())
				? new Failures(1, now.plus(WINDOW))
				: new Failures(current.count() + 1, current.windowEnd()));
	}

	private Instant blockedUntil(String key, int limit, Instant now) {
		Failures current = failures.get(key);
		return current != null && current.count() >= limit && now.isBefore(current.windowEnd())
				? current.windowEnd()
				: null;
	}

	private static Instant later(Instant a, Instant b) {
		if (a == null || b == null) {
			return a == null ? b : a;
		}
		return a.isAfter(b) ? a : b;
	}

	private static String emailKey(String email) {
		return "email:" + User.normalizeEmail(email);
	}

	private static String ipKey(String ip) {
		return "ip:" + ip;
	}

}
