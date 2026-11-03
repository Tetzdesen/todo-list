// Recursos compartilhados pelos testes: conta nova por teste e checagem de erros de JavaScript.
const crypto = require('node:crypto');
const base = require('@playwright/test');

// Cria uma conta pela API (mais rápido que pela tela). O cookie de sessão fica no contexto da página.
async function registerAccount(request, { name = 'Teste E2E' } = {}) {
	const account = {
		name,
		email: `e2e.${crypto.randomUUID()}@exemplo.test`,
		password: crypto.randomBytes(12).toString('hex'),
	};
	// /api/auth/providers entrega o cookie com o token CSRF
	await request.get('/api/auth/providers');
	const cookies = await request.storageState();
	const token = cookies.cookies.find((cookie) => cookie.name === 'XSRF-TOKEN').value;
	const response = await request.post('/api/auth/register', {
		data: account,
		headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) },
	});
	base.expect(response.status()).toBe(201);
	return account;
}

async function createTodo(request, title, description = null) {
	const cookies = await request.storageState();
	const token = cookies.cookies.find((cookie) => cookie.name === 'XSRF-TOKEN').value;
	const response = await request.post('/api/todos', {
		data: { title, description },
		headers: { 'X-XSRF-TOKEN': decodeURIComponent(token) },
	});
	base.expect(response.status()).toBe(201);
	return response.json();
}

const test = base.test.extend({
	// Falha o teste se a página lançar exceção de JavaScript. Respostas 4xx esperadas (sem sessão, validação)
	// também aparecem como erro no console, então só exceções contam.
	page: async ({ page }, use) => {
		const errors = [];
		page.on('pageerror', (error) => errors.push(error.message));
		await use(page);
		base.expect(errors, 'exceções de JavaScript na página').toEqual([]);
	},

	// Usuário logado na lista de tarefas, já com a página carregada
	account: async ({ page }, use) => {
		const account = await registerAccount(page.request);
		await use(account);
	},
});

// Abre a lista e espera ela aparecer (a página só é mostrada depois de confirmar a sessão)
async function openList(page) {
	await page.goto('/');
	await base.expect(page.locator('.page')).toBeVisible();
}

module.exports = { test, expect: base.expect, registerAccount, createTodo, openList };
