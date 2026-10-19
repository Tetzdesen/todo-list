const API_URL = '/api/todos';

const todayFormat = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
const itemDateFormat = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' });

const FILTER_PARAMS = {
	all: '',
	pending: '?completed=false',
	completed: '?completed=true',
};

// Estado da interface: as tarefas sempre vêm da API; aqui fica só o que é da tela
const state = {
	todos: [],
	filter: 'all',
	editingId: null,
	confirmingId: null,
};

const elements = {
	createForm: document.querySelector('#create-form'),
	list: document.querySelector('#todo-list'),
	emptyMessage: document.querySelector('#empty-message'),
	filters: document.querySelectorAll('.tab'),
	counter: document.querySelector('#counter'),
	today: document.querySelector('#today'),
	template: document.querySelector('#todo-template'),
	errorBanner: document.querySelector('#error-banner'),
	errorMessage: document.querySelector('#error-message'),
	errorClose: document.querySelector('#error-close'),
};

// ---------- Comunicação com a API ----------

class ApiError extends Error {
	constructor(message, status, fieldErrors = []) {
		super(message);
		this.status = status;
		this.fieldErrors = fieldErrors;
	}
}

async function request(path, options = {}) {
	const headers = options.body ? { 'Content-Type': 'application/json' } : {};
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
		throw new ApiError(body?.message ?? `Erro ${response.status}`, response.status, body?.fieldErrors ?? []);
	}
	return body;
}

const api = {
	list: (filter) => request(API_URL + FILTER_PARAMS[filter]),
	create: (data) => request(API_URL, { method: 'POST', body: JSON.stringify(data) }),
	update: (id, data) => request(`${API_URL}/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
	toggle: (id) => request(`${API_URL}/${id}/toggle`, { method: 'PATCH' }),
	remove: (id) => request(`${API_URL}/${id}`, { method: 'DELETE' }),
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

function showFieldErrors(form, fieldErrors) {
	clearFieldErrors(form);
	for (const { field, message } of fieldErrors) {
		const target = form.querySelector(`[data-error-for="${field}"]`);
		if (target) {
			// mais de um erro no mesmo campo: mostra todos, um por linha
			target.textContent = target.textContent ? `${target.textContent}\n${message}` : message;
			target.closest('.field').classList.add('invalid');
		} else {
			showBanner(message);
		}
	}
}

function clearFieldErrors(form) {
	for (const target of form.querySelectorAll('[data-error-for]')) {
		target.textContent = '';
		target.closest('.field').classList.remove('invalid');
	}
}

// Erros com fieldErrors vão para o formulário; os demais, para o aviso do topo
async function handleError(error, form) {
	if (form && error.fieldErrors?.length) {
		hideBanner();
		showFieldErrors(form, error.fieldErrors);
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
		state.todos = await api.list(state.filter);
		if (!keepBanner) {
			hideBanner();
		}
	} catch (error) {
		showBanner(error.message);
	}
	render();
}

function render() {
	const items = state.todos.map(createTodoElement);
	elements.list.replaceChildren(...items);
	elements.emptyMessage.hidden = state.todos.length > 0;
	elements.counter.textContent = describeCount(state.todos);

	for (const button of elements.filters) {
		button.setAttribute('aria-pressed', String(button.dataset.filter === state.filter));
	}
}

function plural(count, singular, pluralForm) {
	return `${count} ${count === 1 ? singular : pluralForm}`;
}

function describeCount(todos) {
	const done = todos.filter((todo) => todo.completed).length;
	switch (state.filter) {
		case 'pending':
			return plural(todos.length, 'pendente', 'pendentes');
		case 'completed':
			return plural(todos.length, 'concluída', 'concluídas');
		default:
			return todos.length === 0 ? '' : `${plural(todos.length, 'tarefa', 'tarefas')} · ${plural(done, 'feita', 'feitas')}`;
	}
}

function createTodoElement(todo) {
	const item = elements.template.content.firstElementChild.cloneNode(true);
	item.dataset.id = todo.id;
	item.classList.toggle('completed', todo.completed);

	// textContent garante que o texto do usuário nunca é interpretado como HTML
	item.querySelector('.todo-title').textContent = todo.title;
	item.querySelector('.todo-description').textContent = todo.description ?? '';
	item.querySelector('.todo-confirm-title').textContent = todo.title;

	const date = item.querySelector('.todo-date');
	date.dateTime = todo.createdAt;
	// "19 de out." -> "19 out"
	date.textContent = itemDateFormat.format(new Date(todo.createdAt)).replace(' de ', ' ').replace('.', '');

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
	elements.list.querySelector(`[data-id="${id}"] input[name="title"]`).focus();
}

function setConfirming(id) {
	state.confirmingId = id;
	state.editingId = null;
	render();
}

// ---------- Eventos ----------

elements.createForm.addEventListener('submit', createTodo);

elements.errorClose.addEventListener('click', hideBanner);

for (const button of elements.filters) {
	button.addEventListener('click', () => {
		state.filter = button.dataset.filter;
		state.editingId = null;
		state.confirmingId = null;
		loadTodos();
	});
}

elements.list.addEventListener('click', (event) => {
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

elements.list.addEventListener('change', (event) => {
	if (event.target.matches('.todo-toggle')) {
		toggleTodo(Number(event.target.closest('.todo').dataset.id));
	}
});

elements.list.addEventListener('submit', (event) => {
	if (event.target.matches('.todo-edit')) {
		event.preventDefault();
		saveEdit(event.target, Number(event.target.closest('.todo').dataset.id));
	}
});

elements.today.textContent = todayFormat.format(new Date());
loadTodos();
