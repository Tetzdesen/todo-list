const API_URL = '/api/todos';

const monthFormat = new Intl.DateTimeFormat('pt-BR', { month: 'long' });
const weekdayFormat = new Intl.DateTimeFormat('pt-BR', { weekday: 'long' });
const itemDateFormat = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short' });

const EMPTY_MESSAGES = {
	all: 'Nenhuma tarefa por aqui. Que tal começar pela primeira?',
	pending: 'Nada pendente. Pode descansar.',
	completed: 'Nada feito ainda — mas o dia não acabou.',
};

// Estado da interface: as tarefas sempre vêm da API; aqui fica só o que é da tela.
// A lista inteira é carregada para que as contagens e o progresso fiquem corretos em qualquer filtro.
const state = {
	todos: [],
	filter: 'all',
	editingId: null,
	confirmingId: null,
	justCheckedId: null,
};

const elements = {
	createForm: document.querySelector('#create-form'),
	// pendentes e feitas ficam em listas separadas; os eventos são tratados na área que contém as duas
	board: document.querySelector('.board'),
	greeting: document.querySelector('#greeting'),
	summary: document.querySelector('#summary'),
	list: document.querySelector('#todo-list'),
	doneSection: document.querySelector('#done-section'),
	doneList: document.querySelector('#done-list'),
	emptyMessage: document.querySelector('#empty-message'),
	filters: document.querySelectorAll('.filter'),
	filterCounts: document.querySelectorAll('[data-count-for]'),
	progressLabel: document.querySelector('#progress-label'),
	tally: document.querySelector('#tally'),
	day: document.querySelector('#day'),
	month: document.querySelector('#month'),
	weekday: document.querySelector('#weekday'),
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
	list: () => request(API_URL),
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
		state.todos = await api.list();
		if (!keepBanner) {
			hideBanner();
		}
	} catch (error) {
		showBanner(error.message);
	}
	render();
}

function render() {
	const pending = state.todos.filter((todo) => !todo.completed);
	const done = state.todos.filter((todo) => todo.completed);

	// em "Todas", as feitas descem para um grupo próprio no fim da folha
	const main = { all: pending, pending, completed: done }[state.filter];
	const below = state.filter === 'all' ? done : [];

	elements.list.replaceChildren(...main.map(createTodoElement));
	elements.doneList.replaceChildren(...below.map(createTodoElement));
	elements.doneSection.hidden = below.length === 0;

	const isEmpty = main.length === 0 && below.length === 0;
	elements.emptyMessage.hidden = !isEmpty;
	elements.emptyMessage.textContent = EMPTY_MESSAGES[state.filter];

	renderProgress(pending.length, done.length);
	elements.summary.textContent = describeSummary(pending.length, done.length);
	const counts = { all: state.todos.length, pending: pending.length, completed: done.length };
	for (const target of elements.filterCounts) {
		target.textContent = counts[target.dataset.countFor];
	}
	for (const button of elements.filters) {
		button.setAttribute('aria-pressed', String(button.dataset.filter === state.filter));
	}
	state.justCheckedId = null;
}

function plural(count, singular, pluralForm) {
	return `${count} ${count === 1 ? singular : pluralForm}`;
}

function renderProgress(pendingCount, doneCount) {
	const total = pendingCount + doneCount;
	if (total === 0) {
		elements.progressLabel.textContent = 'Nenhuma tarefa ainda';
	} else if (pendingCount === 0) {
		elements.progressLabel.textContent = 'Tudo feito por hoje';
	} else {
		elements.progressLabel.textContent = `${doneCount} de ${plural(total, 'feita', 'feitas')}`;
	}

	const marks = Array.from({ length: total }, (_, index) => {
		const mark = document.createElement('span');
		mark.classList.toggle('done', index < doneCount);
		return mark;
	});
	elements.tally.replaceChildren(...marks);
}

function describeSummary(pendingCount, doneCount) {
	if (pendingCount + doneCount === 0) {
		return 'Sua lista está vazia.';
	}
	if (pendingCount === 0) {
		return 'Nada pendente. Bom trabalho.';
	}
	return `Você tem ${plural(pendingCount, 'tarefa pendente', 'tarefas pendentes')}.`;
}

function greetingFor(date) {
	const hour = date.getHours();
	if (hour >= 5 && hour < 12) {
		return 'Bom dia.';
	}
	if (hour >= 12 && hour < 18) {
		return 'Boa tarde.';
	}
	return 'Boa noite.';
}

function startOfDay(date) {
	return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

// "hoje", "ontem" ou "18 out"
function describeDate(isoDate) {
	const date = new Date(isoDate);
	const days = Math.round((startOfDay(new Date()) - startOfDay(date)) / 86_400_000);
	if (days === 0) {
		return 'hoje';
	}
	if (days === 1) {
		return 'ontem';
	}
	return itemDateFormat.format(date).replace(' de ', ' ').replace('.', '');
}

function createTodoElement(todo) {
	const item = elements.template.content.firstElementChild.cloneNode(true);
	item.dataset.id = todo.id;
	item.classList.toggle('completed', todo.completed);
	item.classList.toggle('just-checked', todo.id === state.justCheckedId);

	// textContent garante que o texto do usuário nunca é interpretado como HTML
	item.querySelector('.todo-title').textContent = todo.title;
	item.querySelector('.todo-description').textContent = todo.description ?? '';
	item.querySelector('.todo-confirm-title').textContent = todo.title;

	const date = item.querySelector('.todo-date');
	date.dateTime = todo.createdAt;
	date.textContent = describeDate(todo.createdAt);

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
		const toggled = await api.toggle(id);
		state.justCheckedId = toggled?.completed ? id : null;
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

// N (ou /) começa uma tarefa nova; Esc cancela a edição ou a remoção em andamento
document.addEventListener('keydown', (event) => {
	const typing = event.target.closest('input, textarea, [contenteditable]');
	if (event.key === 'Escape' && (state.editingId !== null || state.confirmingId !== null)) {
		state.editingId = null;
		state.confirmingId = null;
		render();
		return;
	}
	if (!typing && !event.ctrlKey && !event.metaKey && !event.altKey && (event.key === 'n' || event.key === '/')) {
		event.preventDefault();
		elements.createForm.elements.title.focus();
	}
});

const today = new Date();
elements.day.textContent = today.getDate();
elements.month.textContent = monthFormat.format(today);
elements.weekday.textContent = weekdayFormat.format(today);
elements.greeting.textContent = greetingFor(today);
loadTodos();
