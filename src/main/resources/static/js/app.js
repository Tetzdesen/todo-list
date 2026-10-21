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
};

// ---------- Comunicação com a API ----------

const api = {
	list: () => request(API_URL),
	create: (data) => request(API_URL, { method: 'POST', body: JSON.stringify(data) }),
	update: (id, data) => request(`${API_URL}/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
	toggle: (id) => request(`${API_URL}/${id}/toggle`, { method: 'PATCH' }),
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
	if (error.status === 404) {
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
}

// "1 de 3 concluídas" com a barra proporcional; escondido enquanto não há tarefas
function showProgress(done, total) {
	elements.progress.hidden = total === 0;
	elements.progressLabel.textContent = `${done} de ${total} ${total === 1 ? 'concluída' : 'concluídas'}`;
	elements.progressBar.style.width = total === 0 ? '0' : `${(done / total) * 100}%`;
}

function startOfDay(date) {
	return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

// "Hoje", "Ontem" ou "18 de out." (a data completa fica na dica do elemento)
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

	// textContent garante que o texto do usuário nunca é interpretado como HTML
	item.querySelector('.todo-title').textContent = todo.title;
	const description = item.querySelector('.todo-description');
	description.textContent = todo.description ?? '';
	description.hidden = !todo.description;
	const created = item.querySelector('.todo-created');
	const createdAt = new Date(todo.createdAt);
	created.dateTime = todo.createdAt;
	created.textContent = describeCreation(createdAt);
	created.title = `Criada em ${fullDateFormat.format(createdAt)}`;
	item.querySelector('.todo-confirm-title').textContent = todo.title;

	const toggle = item.querySelector('.todo-toggle');
	toggle.checked = todo.completed;
	toggle.setAttribute('aria-label', todo.completed ? 'Marcar como pendente' : 'Marcar como concluída');

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

async function createTodo(event) {
	event.preventDefault();
	const form = elements.createForm;
	try {
		await api.create(readForm(form));
		form.reset();
		clearFieldErrors(form);
		await loadTodos();
		form.elements.title.focus();
	} catch (error) {
		await handleError(error, form);
	}
}

async function saveEdit(form, id) {
	const todo = findTodo(id);
	try {
		await api.update(id, { ...readForm(form), completed: todo.completed });
		state.editingId = null;
		await loadTodos();
	} catch (error) {
		await handleError(error, form);
	}
}

async function toggleTodo(id) {
	try {
		await api.toggle(id);
		await loadTodos();
	} catch (error) {
		await handleError(error);
	}
}

async function removeTodo(id) {
	try {
		await api.remove(id);
		state.confirmingId = null;
		await loadTodos();
	} catch (error) {
		await handleError(error);
	}
}

function startEdit(id) {
	state.editingId = id;
	state.confirmingId = null;
	render();
	elements.board.querySelector(`[data-id="${id}"] input[name="title"]`).focus();
}

function setConfirming(id) {
	state.confirmingId = id;
	state.editingId = null;
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
			state.editingId = null;
			render();
			break;
		case 'delete':
			setConfirming(id);
			break;
		case 'confirm-delete':
			removeTodo(id);
			break;
		case 'cancel-delete':
			setConfirming(null);
			break;
	}
});

elements.board.addEventListener('change', (event) => {
	if (event.target.matches('.todo-toggle')) {
		toggleTodo(Number(event.target.closest('.todo').dataset.id));
	}
});

elements.board.addEventListener('submit', (event) => {
	if (event.target.matches('.todo-edit')) {
		event.preventDefault();
		saveEdit(event.target, Number(event.target.closest('.todo').dataset.id));
	}
});

// N (ou /) começa uma tarefa nova; Esc fecha a tarefa aberta, a remoção em andamento ou a nova tarefa
document.addEventListener('keydown', (event) => {
	if (event.key === 'Escape') {
		if (state.editingId !== null || state.confirmingId !== null) {
			state.editingId = null;
			state.confirmingId = null;
			render();
		} else if (elements.createForm.contains(document.activeElement)) {
			document.activeElement.blur();
		}
		return;
	}
	const typing = event.target instanceof Element && event.target.closest('input, textarea, [contenteditable]');
	if (!typing && !event.ctrlKey && !event.metaKey && !event.altKey && (event.key === 'n' || event.key === '/')) {
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
