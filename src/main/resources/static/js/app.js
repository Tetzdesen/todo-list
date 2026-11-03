import { clearFieldErrors, request, showFieldErrors } from './api.js';

const API_URL = '/api/todos';

const shortDateFormat = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short' });
const fullDateFormat = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeStyle: 'short' });
const todayFormat = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });

const EMPTY_MESSAGES = {
	all: 'Nenhuma tarefa ainda. Use o campo acima para adicionar a primeira.',
	pending: 'Nenhuma tarefa pendente.',
	completed: 'Nenhuma tarefa concluída.',
};

// Estado da interface: as tarefas sempre vêm da API; aqui fica só o que é da tela.
// A lista inteira é carregada para que as contagens da lateral fiquem corretas em qualquer filtro.
const state = {
	todos: [],
	filter: 'all',
	editingId: null,
	confirmingId: null,
	// tarefa que acabou de ser concluída: só ela desenha o risco na próxima vez que a lista aparece
	justCompletedId: null,
	// tarefas com a marcação a caminho do servidor: um segundo clique antes da resposta é ignorado
	toggling: new Set(),
	// onde o foco do teclado deve ficar depois que a lista for redesenhada ({ id, selector })
	focusAfterRender: null,
};

const elements = {
	page: document.querySelector('.page'),
	accountName: document.querySelector('#account-name'),
	accountAvatar: document.querySelector('#account-avatar'),
	today: document.querySelector('#today'),
	progress: document.querySelector('#progress'),
	progressLabel: document.querySelector('#progress-label'),
	progressBar: document.querySelector('#progress-bar'),
	logout: document.querySelector('#logout'),
	createForm: document.querySelector('#create-form'),
	// os eventos das tarefas são tratados na área principal, que contém a lista
	board: document.querySelector('main'),
	list: document.querySelector('#todo-list'),
	emptyMessage: document.querySelector('#empty-message'),
	emptyText: document.querySelector('#empty-text'),
	filters: document.querySelectorAll('.filter'),
	filterCounts: document.querySelectorAll('[data-count-for]'),
	template: document.querySelector('#todo-template'),
	errorBanner: document.querySelector('#error-banner'),
	errorMessage: document.querySelector('#error-message'),
	errorClose: document.querySelector('#error-close'),
	status: document.querySelector('#status'),
};

// ---------- Comunicação com a API ----------

const api = {
	// a API devolve as tarefas em páginas; a tela busca todas, porque os contadores e o progresso usam a lista inteira
	list: async () => {
		const todos = [];
		for (let page = 0; ; page++) {
			const result = await request(`${API_URL}?page=${page}&size=100`);
			todos.push(...result.content);
			if (page + 1 >= result.totalPages) {
				return todos;
			}
		}
	},
	create: (data) => request(API_URL, { method: 'POST', body: JSON.stringify(data) }),
	// alteração parcial; a versão faz o servidor recusar (409) se a tarefa mudou em outra aba
	update: (id, data) => request(`${API_URL}/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
	remove: (id) => request(`${API_URL}/${id}`, { method: 'DELETE' }),
	me: () => request('/api/auth/me'),
	logout: () => request('/api/auth/logout', { method: 'POST' }),
};

// ---------- Mensagens de erro ----------

function showBanner(message) {
	elements.errorMessage.textContent = message;
	elements.errorBanner.hidden = false;
}

function hideBanner() {
	elements.errorBanner.hidden = true;
	elements.errorMessage.textContent = '';
}

// ---------- Anúncios para leitores de tela ----------

// O resultado de cada ação vai para uma região role="status": o leitor de tela lê sem tirar o foco do lugar.
// O texto é limpo antes, para a mesma frase ser lida de novo se a ação se repetir.
function announce(message) {
	elements.status.textContent = '';
	setTimeout(() => {
		elements.status.textContent = message;
	}, 50);
}

// "3 de 5 concluídas", a mesma frase do progresso no topo
function progressText() {
	const done = state.todos.filter((todo) => todo.completed).length;
	const total = state.todos.length;
	return `${done} de ${total} ${total === 1 ? 'concluída' : 'concluídas'}`;
}

// ---------- Sessão ----------

// replace: voltar no navegador a partir da tela de entrada não traz de volta uma lista sem sessão
function goToLogin() {
	location.replace('login.html');
}

function showUser(user) {
	elements.accountName.textContent = user.name;
	elements.accountName.title = user.email;
	elements.accountAvatar.textContent = initials(user.name);
}

// "Ana Maria Souza" → "AS"; um nome só → a primeira letra
function initials(name) {
	const parts = name.trim().split(/\s+/);
	const letters = parts.length > 1 ? parts[0][0] + parts.at(-1)[0] : parts[0][0];
	return letters.toUpperCase();
}

async function logout() {
	try {
		await api.logout();
		goToLogin();
	} catch (error) {
		showBanner(error.message);
	}
}

// Erros com fieldErrors vão para o formulário; os demais, para o aviso do topo
async function handleError(error, form) {
	if (error.status === 401) {
		goToLogin();
		return;
	}
	// sair em outra aba apaga também o cookie do token CSRF: a próxima alteração recebe 403, não 401.
	// Consultar a sessão diz qual é o caso e, se ela ainda vale, grava um token novo para a próxima tentativa.
	if (error.status === 403) {
		try {
			await api.me();
		} catch (meError) {
			if (meError.status === 401) {
				goToLogin();
				return;
			}
		}
	}
	if (form && error.fieldErrors?.length) {
		hideBanner();
		for (const message of showFieldErrors(form, error.fieldErrors)) {
			showBanner(message);
		}
		return;
	}
	showBanner(error.message);
	// 404: a tarefa foi removida; 409: foi alterada em outro lugar. Nos dois casos, a lista é recarregada
	if (error.status === 404 || error.status === 409) {
		state.editingId = null;
		state.confirmingId = null;
		await loadTodos({ keepBanner: true });
	} else {
		render();
	}
}

// ---------- Lista ----------

async function loadTodos({ keepBanner = false } = {}) {
	try {
		state.todos = await api.list();
		if (!keepBanner) {
			hideBanner();
		}
	} catch (error) {
		if (error.status === 401) {
			goToLogin();
			return;
		}
		showBanner(error.message);
	}
	render();
}

function render() {
	const pending = state.todos.filter((todo) => !todo.completed);
	const done = state.todos.filter((todo) => todo.completed);

	// em "Todas", as feitas vêm depois das pendentes
	const visible = { all: [...pending, ...done], pending, completed: done }[state.filter];

	// redesenhar a lista recria os elementos: sem isto, o foco do teclado voltaria para o início da página
	const focus = state.focusAfterRender ?? focusInList();
	state.focusAfterRender = null;

	elements.list.replaceChildren(...visible.map(createTodoElement));
	elements.list.hidden = visible.length === 0;
	elements.emptyMessage.hidden = visible.length > 0;
	elements.emptyText.textContent = EMPTY_MESSAGES[state.filter];

	showProgress(done.length, state.todos.length);

	const counts = { all: state.todos.length, pending: pending.length, completed: done.length };
	for (const target of elements.filterCounts) {
		target.textContent = counts[target.dataset.countFor];
	}
	for (const button of elements.filters) {
		button.setAttribute('aria-pressed', String(button.dataset.filter === state.filter));
	}

	restoreFocus(focus);
}

// O controle da lista que está com o foco agora, descrito de um jeito que sobrevive ao redesenho
function focusInList() {
	const active = document.activeElement;
	if (!elements.list.contains(active)) {
		return null;
	}
	const id = Number(active.closest('.todo').dataset.id);
	if (active.matches('.todo-toggle')) {
		return { id, selector: '.todo-toggle' };
	}
	return active.dataset.action ? { id, selector: `[data-action="${active.dataset.action}"]` } : null;
}

// Devolve o foco ao mesmo controle da mesma tarefa. Se ela saiu da lista (outro filtro), o foco vai para o
// filtro ativo; com { newTask: true }, vai para o campo de nova tarefa.
function restoreFocus(focus) {
	if (!focus) {
		return;
	}
	if (focus.newTask) {
		elements.createForm.elements.title.focus();
		return;
	}
	const target = elements.list.querySelector(`[data-id="${focus.id}"] ${focus.selector}`);
	if (target && target.offsetParent !== null) {
		target.focus();
	} else {
		document.querySelector('.filter[aria-pressed="true"]').focus();
	}
}

// "1 de 3 concluídas" com a barra proporcional; escondido enquanto não há tarefas
function showProgress(done, total) {
	elements.progress.hidden = total === 0;
	elements.progressLabel.textContent = progressText();
	elements.progressBar.style.width = total === 0 ? '0' : `${(done / total) * 100}%`;
}

function startOfDay(date) {
	return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

// "Hoje", "Ontem" ou "18 de out." (a data completa fica na dica e no texto para leitores de tela)
function describeCreation(date) {
	const days = Math.round((startOfDay(new Date()) - startOfDay(date)) / 86_400_000);
	if (days === 0) {
		return 'Hoje';
	}
	if (days === 1) {
		return 'Ontem';
	}
	return shortDateFormat.format(date);
}

function createTodoElement(todo) {
	const item = elements.template.content.firstElementChild.cloneNode(true);
	item.dataset.id = todo.id;
	item.classList.toggle('completed', todo.completed);
	item.classList.toggle('just-done', todo.completed && todo.id === state.justCompletedId);

	// textContent garante que o texto do usuário nunca é interpretado como HTML
	item.querySelector('.todo-title span').textContent = todo.title;
	const description = item.querySelector('.todo-description');
	description.textContent = todo.description ?? '';
	description.hidden = !todo.description;
	const created = item.querySelector('.todo-created');
	const createdAt = new Date(todo.createdAt);
	const fullDate = `Criada em ${fullDateFormat.format(createdAt)}`;
	created.dateTime = todo.createdAt;
	created.title = fullDate;
	const shortText = document.createElement('span');
	shortText.ariaHidden = 'true';
	shortText.textContent = describeCreation(createdAt);
	const fullText = document.createElement('span');
	fullText.className = 'visually-hidden';
	fullText.textContent = fullDate;
	created.replaceChildren(shortText, fullText);
	item.querySelector('.todo-confirm-title').textContent = todo.title;

	// os nomes acessíveis citam a tarefa: numa lista, "Editar tarefa" repetido não diz qual é
	const toggle = item.querySelector('.todo-toggle');
	toggle.checked = todo.completed;
	toggle.setAttribute('aria-label', todo.completed
		? `Marcar “${todo.title}” como pendente`
		: `Marcar “${todo.title}” como concluída`);
	item.querySelector('[data-action="edit"]').setAttribute('aria-label', `Editar “${todo.title}”`);
	item.querySelector('[data-action="delete"]').setAttribute('aria-label', `Excluir “${todo.title}”`);

	if (todo.id === state.editingId) {
		item.dataset.mode = 'edit';
		const form = item.querySelector('.todo-edit');
		form.elements.title.value = todo.title;
		form.elements.description.value = todo.description ?? '';
	} else if (todo.id === state.confirmingId) {
		item.dataset.mode = 'confirm';
	}
	return item;
}

function findTodo(id) {
	return state.todos.find((todo) => todo.id === id);
}

// ---------- Ações ----------

function readForm(form) {
	const description = form.elements.description.value;
	return {
		title: form.elements.title.value,
		description: description === '' ? null : description,
	};
}

// enquanto o servidor responde, o botão de envio fica desativado: um segundo clique não duplica a ação
async function whileSaving(form, action) {
	const button = form.querySelector('[type="submit"]');
	button.disabled = true;
	try {
		await action();
	} finally {
		button.disabled = false;
	}
}

async function createTodo(event) {
	event.preventDefault();
	const form = elements.createForm;
	if (form.querySelector('[type="submit"]').disabled) {
		return;
	}
	await whileSaving(form, () => saveNewTodo(form));
}

async function saveNewTodo(form) {
	try {
		const created = await api.create(readForm(form));
		form.reset();
		clearFieldErrors(form);
		await loadTodos();
		form.elements.title.focus();
		announce(`Tarefa “${created.title}” adicionada. ${progressText()}.`);
	} catch (error) {
		await handleError(error, form);
	}
}

async function saveEdit(form, id) {
	const todo = findTodo(id);
	try {
		await api.update(id, { ...readForm(form), version: todo.version });
		state.editingId = null;
		state.focusAfterRender = { id, selector: '[data-action="edit"]' };
		await loadTodos();
		announce('Alterações salvas.');
	} catch (error) {
		await handleError(error, form);
	}
}

async function toggleTodo(id) {
	state.toggling.add(id);
	try {
		const todo = findTodo(id);
		await api.update(id, { completed: !todo.completed, version: todo.version });
		state.justCompletedId = todo.completed ? null : id;
		await loadTodos();
		announce(`“${todo.title}” ${todo.completed ? 'voltou para pendentes' : 'concluída'}. ${progressText()}.`);
	} catch (error) {
		await handleError(error);
	} finally {
		state.justCompletedId = null;
		state.toggling.delete(id);
	}
}

async function removeTodo(id) {
	try {
		const todo = findTodo(id);
		await api.remove(id);
		state.confirmingId = null;
		// o foco passa para a tarefa que ocupa o lugar da excluída (ou a anterior); sem nenhuma, para a nova tarefa
		const item = elements.list.querySelector(`[data-id="${id}"]`);
		const neighbor = item?.nextElementSibling ?? item?.previousElementSibling;
		state.focusAfterRender = neighbor
			? { id: Number(neighbor.dataset.id), selector: '.todo-toggle' }
			: { newTask: true };
		await loadTodos();
		announce(`“${todo.title}” excluída. ${progressText()}.`);
	} catch (error) {
		await handleError(error);
	}
}

function startEdit(id) {
	state.editingId = id;
	state.confirmingId = null;
	state.focusAfterRender = { id, selector: 'input[name="title"]' };
	render();
}

// abre a confirmação de exclusão com o foco em "Cancelar", a opção segura
function setConfirming(id) {
	state.confirmingId = id;
	state.editingId = null;
	state.focusAfterRender = { id, selector: '[data-action="cancel-delete"]' };
	render();
}

// fecha a edição ou a confirmação e devolve o foco ao botão que a abriu
function closeTodoPanel() {
	if (state.editingId !== null) {
		state.focusAfterRender = { id: state.editingId, selector: '[data-action="edit"]' };
	} else if (state.confirmingId !== null) {
		state.focusAfterRender = { id: state.confirmingId, selector: '[data-action="delete"]' };
	}
	state.editingId = null;
	state.confirmingId = null;
	render();
}

// ---------- Eventos ----------

elements.createForm.addEventListener('submit', createTodo);

elements.errorClose.addEventListener('click', hideBanner);

elements.logout.addEventListener('click', logout);

for (const button of elements.filters) {
	button.addEventListener('click', () => {
		state.filter = button.dataset.filter;
		state.editingId = null;
		state.confirmingId = null;
		loadTodos();
	});
}

elements.board.addEventListener('click', (event) => {
	const button = event.target.closest('[data-action]');
	if (!button) {
		return;
	}
	const id = Number(button.closest('.todo').dataset.id);

	switch (button.dataset.action) {
		case 'edit':
			startEdit(id);
			break;
		case 'cancel-edit':
			closeTodoPanel();
			break;
		case 'delete':
			setConfirming(id);
			break;
		case 'confirm-delete':
			removeTodo(id);
			break;
		case 'cancel-delete':
			closeTodoPanel();
			break;
	}
});

elements.board.addEventListener('change', (event) => {
	if (event.target.matches('.todo-toggle')) {
		const id = Number(event.target.closest('.todo').dataset.id);
		if (state.toggling.has(id)) {
			// ainda esperando a resposta anterior: desfaz o clique na tela e não manda outra alteração
			event.target.checked = !event.target.checked;
			return;
		}
		toggleTodo(id);
	}
});

elements.board.addEventListener('submit', (event) => {
	if (event.target.matches('.todo-edit')) {
		event.preventDefault();
		const form = event.target;
		if (!form.querySelector('[type="submit"]').disabled) {
			whileSaving(form, () => saveEdit(form, Number(form.closest('.todo').dataset.id)));
		}
	}
});

// Alt+N começa uma tarefa nova; Esc fecha a tarefa aberta, a remoção em andamento ou a nova tarefa.
// O atalho usa Alt para não disparar sem querer (atalhos de uma tecla só atrapalham quem usa entrada por voz).
document.addEventListener('keydown', (event) => {
	if (event.key === 'Escape') {
		if (state.editingId !== null || state.confirmingId !== null) {
			closeTodoPanel();
		} else if (elements.createForm.contains(document.activeElement)) {
			document.activeElement.blur();
		}
		return;
	}
	// event.code: no Mac, Option+N produz outro caractere em event.key
	if (event.altKey && !event.ctrlKey && !event.metaKey && event.code === 'KeyN') {
		event.preventDefault();
		elements.createForm.elements.title.focus();
	}
});

// A página só aparece depois de confirmar a sessão; sem ela, vai para a tela de entrada
async function start() {
	// "Terça-feira, 21 de outubro"
	const today = todayFormat.format(new Date());
	elements.today.textContent = today.charAt(0).toUpperCase() + today.slice(1);
	try {
		showUser(await api.me());
	} catch (error) {
		if (error.status === 401) {
			goToLogin();
			return;
		}
		showBanner(error.message);
	}
	elements.page.hidden = false;
	await loadTodos({ keepBanner: !elements.errorBanner.hidden });
}

start();
