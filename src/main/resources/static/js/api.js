// Comunicação com a API, usada pela lista de tarefas (app.js) e pela tela de entrada (login.js)

export class ApiError extends Error {
	constructor(message, status, fieldErrors = []) {
		super(message);
		this.status = status;
		this.fieldErrors = fieldErrors;
	}
}

const METHODS_WITH_CSRF = ['POST', 'PUT', 'PATCH', 'DELETE'];

// O servidor entrega o token anti-CSRF no cookie XSRF-TOKEN; ele volta no cabeçalho X-XSRF-TOKEN.
// O cookie é lido a cada requisição porque o login troca o token.
function csrfToken() {
	const cookie = document.cookie.split('; ').find((entry) => entry.startsWith('XSRF-TOKEN='));
	return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : null;
}

export async function request(path, options = {}) {
	const headers = options.body ? { 'Content-Type': 'application/json' } : {};
	const token = csrfToken();
	if (token && METHODS_WITH_CSRF.includes(options.method)) {
		headers['X-XSRF-TOKEN'] = token;
	}

	let response;
	try {
		response = await fetch(path, { ...options, headers });
	} catch {
		throw new ApiError('Não foi possível falar com o servidor. Verifique se a aplicação está rodando.', 0);
	}

	if (response.status === 204) {
		return null;
	}

	const body = await response.json().catch(() => null);
	if (!response.ok) {
		// erros no formato da RFC 9457 (application/problem+json): a mensagem vem em "detail"
		throw new ApiError(body?.detail ?? `Erro ${response.status}`, response.status, body?.fieldErrors ?? []);
	}
	return body;
}

let errorIdCounter = 0;

// o campo que um aviso de erro descreve: o primeiro do mesmo bloco .field
function fieldInput(target) {
	return target.closest('.field').querySelector('input, textarea');
}

// Liga a mensagem ao campo (aria-describedby) e marca o campo como inválido, para o leitor de tela ler o erro
// ao chegar nele. A descrição que o campo já tinha (uma dica, por exemplo) é mantida.
function linkError(input, target) {
	target.id ||= `field-error-${++errorIdCounter}`;
	input.dataset.describedby ??= input.getAttribute('aria-describedby') ?? '';
	input.setAttribute('aria-describedby', `${input.dataset.describedby} ${target.id}`.trim());
	input.setAttribute('aria-invalid', 'true');
}

function unlinkError(input) {
	if (input.dataset.describedby === undefined) {
		return;
	}
	if (input.dataset.describedby) {
		input.setAttribute('aria-describedby', input.dataset.describedby);
	} else {
		input.removeAttribute('aria-describedby');
	}
	delete input.dataset.describedby;
	input.removeAttribute('aria-invalid');
}

// Mostra cada erro junto ao campo de mesmo nome, leva o foco ao primeiro campo com erro
// e devolve as mensagens que não têm campo no formulário
export function showFieldErrors(form, fieldErrors) {
	clearFieldErrors(form);
	const unmatched = [];
	for (const { field, message } of fieldErrors) {
		const target = form.querySelector(`[data-error-for="${field}"]`);
		if (target) {
			// mais de um erro no mesmo campo: mostra todos, um por linha
			target.textContent = target.textContent ? `${target.textContent}\n${message}` : message;
			target.closest('.field').classList.add('invalid');
			const input = fieldInput(target);
			linkError(input, target);
		} else {
			unmatched.push(message);
		}
	}
	// o primeiro na ordem da tela, não na ordem em que o servidor listou os erros
	form.querySelector('[aria-invalid="true"]')?.focus();
	return unmatched;
}

export function clearFieldErrors(form) {
	for (const target of form.querySelectorAll('[data-error-for]')) {
		target.textContent = '';
		target.closest('.field').classList.remove('invalid');
		unlinkError(fieldInput(target));
	}
}
