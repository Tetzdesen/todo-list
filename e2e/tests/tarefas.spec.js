// Lista de tarefas: criar, concluir, editar, excluir, filtros, foco do teclado e anúncios para leitores de tela.
const { test, expect, createTodo, openList } = require('./fixtures');

const status = (page) => page.locator('#status');
const row = (page, title) => page.locator('.todo').filter({ has: page.locator('.todo-title', { hasText: title }) });

test.describe('com uma conta nova', () => {
	test.beforeEach(async ({ page, account }) => {
		await openList(page);
	});

	test('cria tarefas pelo Enter, anuncia e devolve o foco ao campo', async ({ page }) => {
		const title = page.locator('#create-title');
		for (const text of ['Pagar a conta de luz', 'Comprar pão']) {
			await title.fill(text);
			await title.press('Enter');
			await expect(row(page, text)).toBeVisible();
		}
		await expect(status(page)).toHaveText('Tarefa “Comprar pão” adicionada. 0 de 2 concluídas.');
		await expect(title).toBeFocused();
		await expect(page.locator('[data-count-for="all"]')).toHaveText('2');
	});

	test('título vazio marca o campo como inválido, com foco e mensagem ligada', async ({ page }) => {
		const title = page.locator('#create-title');
		await page.locator('#create-form [type="submit"]').click();

		await expect(title).toHaveAttribute('aria-invalid', 'true');
		await expect(title).toBeFocused();
		await expect(title).toHaveAccessibleDescription('não deve estar em branco');

		await title.fill('Estudar cálculo');
		await title.press('Enter');
		await expect(row(page, 'Estudar cálculo')).toBeVisible();
		await expect(title).not.toHaveAttribute('aria-invalid');
	});

	test('Alt+N foca a nova tarefa e N sozinho não faz nada', async ({ page }) => {
		const title = page.locator('#create-title');
		await page.keyboard.press('n');
		await expect(title).not.toBeFocused();
		await page.keyboard.press('Alt+KeyN');
		await expect(title).toBeFocused();
	});

	test('o link "Pular para a lista" aparece no primeiro Tab e leva à nova tarefa', async ({ page }) => {
		await page.keyboard.press('Tab');
		const skip = page.getByRole('link', { name: 'Pular para a lista' });
		await expect(skip).toBeFocused();
		await expect(skip).toBeInViewport();
		await page.keyboard.press('Enter');
		await page.keyboard.press('Tab');
		await expect(page.locator('#create-title')).toBeFocused();
	});

	test('o tema escolhido continua depois de recarregar', async ({ page }) => {
		const html = page.locator('html');
		const before = await html.getAttribute('data-theme');
		const after = before === 'dark' ? 'light' : 'dark';
		await page.locator('.band [data-theme-toggle]').click();
		await expect(html).toHaveAttribute('data-theme', after);
		await page.reload();
		await expect(html).toHaveAttribute('data-theme', after);
	});
});

test.describe('com tarefas criadas', () => {
	test.beforeEach(async ({ page, account }) => {
		for (const title of ['Pagar a conta de luz', 'Levar o carro na revisão', 'Comprar pão']) {
			await createTodo(page.request, title);
		}
		await openList(page);
	});

	test('os controles de cada linha dizem de qual tarefa são', async ({ page }) => {
		const item = row(page, 'Comprar pão');
		await expect(item.locator('.todo-toggle')).toHaveAccessibleName('Marcar “Comprar pão” como concluída');
		await expect(item.getByRole('button', { name: 'Editar “Comprar pão”' })).toBeAttached();
		await expect(item.getByRole('button', { name: 'Excluir “Comprar pão”' })).toBeAttached();
		await expect(item.locator('.todo-created')).toContainText('Criada em');
	});

	test('concluir pelo teclado mantém o foco, anuncia e atualiza o progresso', async ({ page }) => {
		const toggle = row(page, 'Comprar pão').locator('.todo-toggle');
		await toggle.focus();
		await page.keyboard.press('Space');

		await expect(row(page, 'Comprar pão')).toHaveClass(/completed/);
		const sameToggle = row(page, 'Comprar pão').locator('.todo-toggle');
		await expect(sameToggle).toBeFocused();
		await expect(sameToggle).toHaveAccessibleName('Marcar “Comprar pão” como pendente');
		await expect(status(page)).toHaveText('“Comprar pão” concluída. 1 de 3 concluídas.');
		await expect(page.locator('#progress-label')).toHaveText('1 de 3 concluídas');
		// só a tarefa recém-concluída desenha o risco
		await expect(page.locator('.todo.just-done')).toHaveCount(1);
	});

	test('clique duplo rápido na caixinha não gera erro de conflito', async ({ page }) => {
		// o clique vai no rótulo .check, que tem a área de toque ampliada (é onde a pessoa clica)
		await row(page, 'Pagar a conta de luz').locator('.check').dblclick();
		await expect(page.locator('#progress-label')).toHaveText(/de 3 concluídas/);
		await page.waitForLoadState('networkidle');
		await expect(page.locator('#error-banner')).toBeHidden();
	});

	test('editar foca o título, Esc cancela e salvar devolve o foco a Editar', async ({ page }) => {
		const edit = () => row(page, 'Levar o carro na revisão').getByRole('button', { name: 'Editar “Levar o carro na revisão”' });
		await edit().click();
		await expect(page.locator('.todo[data-mode="edit"] input[name="title"]')).toBeFocused();
		await page.keyboard.press('Escape');
		await expect(edit()).toBeFocused();

		await edit().click();
		const form = page.locator('.todo[data-mode="edit"]');
		await form.locator('input[name="title"]').fill('Levar o carro na oficina');
		await form.locator('textarea[name="description"]').fill('Revisão dos 30 mil km');
		await form.locator('input[name="title"]').press('Enter');

		const saved = row(page, 'Levar o carro na oficina');
		await expect(saved.locator('.todo-description')).toHaveText('Revisão dos 30 mil km');
		await expect(status(page)).toHaveText('Alterações salvas.');
		await expect(saved.getByRole('button', { name: 'Editar “Levar o carro na oficina”' })).toBeFocused();
	});

	test('excluir confirma com foco em Cancelar e depois foca a tarefa seguinte', async ({ page }) => {
		const titles = await page.locator('.todo-title').allTextContents();
		const index = titles.indexOf('Levar o carro na revisão');
		const next = titles[index + 1] ?? titles[index - 1];
		const remove = () => row(page, 'Levar o carro na revisão').getByRole('button', { name: 'Excluir “Levar o carro na revisão”' });

		await remove().click();
		await expect(page.getByRole('button', { name: 'Cancelar' })).toBeFocused();
		await page.keyboard.press('Escape');
		await expect(remove()).toBeFocused();

		await remove().click();
		await page.locator('.todo[data-mode="confirm"]').getByRole('button', { name: 'Excluir', exact: true }).click();

		await expect(row(page, 'Levar o carro na revisão')).toHaveCount(0);
		await expect(row(page, next).locator('.todo-toggle')).toBeFocused();
		await expect(status(page)).toHaveText('“Levar o carro na revisão” excluída. 0 de 2 concluídas.');
	});

	test('os filtros mostram só as tarefas do grupo escolhido', async ({ page }) => {
		await row(page, 'Comprar pão').locator('.check').click();
		await expect(page.locator('[data-count-for="completed"]')).toHaveText('1');

		await page.getByRole('button', { name: /^Concluídas/ }).click();
		await expect(page.locator('.todo')).toHaveCount(1);
		await expect(page.getByRole('button', { name: /^Concluídas/ })).toHaveAttribute('aria-pressed', 'true');

		await page.getByRole('button', { name: /^Pendentes/ }).click();
		await expect(page.locator('.todo')).toHaveCount(2);
	});
});
