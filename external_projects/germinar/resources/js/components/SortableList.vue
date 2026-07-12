<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
    items: { type: Array, default: () => [] },
    urls: { type: Object, required: true },
    labels: { type: Object, default: () => ({}) },
});

const list = ref(props.items.map((item) => ({ ...item })));
const dragIndex = ref(null);
const dirty = ref(false);
const saving = ref(false);
const feedback = ref('');

const csrf =
    document.querySelector('meta[name="csrf-token"]')?.getAttribute('content') ?? '';

const isEmpty = computed(() => list.value.length === 0);

function urlFor(template, item) {
    return template.replace('__ID__', String(item.id));
}

/* — drag & drop nativo — */
function onDragStart(index, event) {
    dragIndex.value = index;
    event.dataTransfer.effectAllowed = 'move';
    // Firefox exige setData para iniciar o arrasto.
    event.dataTransfer.setData('text/plain', String(list.value[index].id));
}

function onDragOver(index) {
    if (dragIndex.value === null || dragIndex.value === index) return;
    const [moved] = list.value.splice(dragIndex.value, 1);
    list.value.splice(index, 0, moved);
    dragIndex.value = index;
    dirty.value = true;
}

function onDragEnd() {
    dragIndex.value = null;
}

/* — ações — */
async function saveOrder() {
    saving.value = true;
    feedback.value = '';
    try {
        const response = await fetch(props.urls.reorder, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': csrf,
                Accept: 'application/json',
            },
            body: JSON.stringify({ ids: list.value.map((item) => item.id) }),
        });
        if (!response.ok) throw new Error(String(response.status));
        dirty.value = false;
        flash('Ordem salva');
    } catch {
        flash('Não foi possível salvar a ordem. Tente de novo.');
    } finally {
        saving.value = false;
    }
}

async function toggle(item) {
    try {
        const response = await fetch(urlFor(props.urls.toggle, item), {
            method: 'PATCH',
            headers: { 'X-CSRF-TOKEN': csrf, Accept: 'application/json' },
        });
        if (!response.ok) throw new Error(String(response.status));
        const data = await response.json();
        item.is_active = Boolean(data.is_active);
    } catch {
        flash('Não foi possível alterar o status. Tente de novo.');
    }
}

async function destroy(item) {
    const message = props.labels.confirmDelete ?? 'Excluir este item?';
    if (!window.confirm(message)) return;
    try {
        const response = await fetch(urlFor(props.urls.destroy, item), {
            method: 'DELETE',
            headers: { 'X-CSRF-TOKEN': csrf, Accept: 'application/json' },
        });
        if (!response.ok) throw new Error(String(response.status));
        list.value = list.value.filter((entry) => entry.id !== item.id);
    } catch {
        flash('Não foi possível excluir. Tente de novo.');
    }
}

let flashTimer = null;
function flash(message) {
    feedback.value = message;
    if (flashTimer) clearTimeout(flashTimer);
    flashTimer = setTimeout(() => (feedback.value = ''), 3000);
}
</script>

<template>
    <div class="sortable">
        <p v-if="isEmpty" class="sortable-empty">
            {{ labels.empty ?? 'Nada por aqui ainda.' }}
        </p>

        <template v-else>
            <div class="sortable-toolbar">
                <button
                    type="button"
                    class="btn btn-primary"
                    :disabled="!dirty || saving"
                    @click="saveOrder"
                >
                    {{ saving ? 'Salvando…' : 'Salvar ordem' }}
                </button>
                <span v-if="feedback" class="sortable-feedback">{{ feedback }}</span>
                <span v-else-if="dirty" class="sortable-hint">
                    A ordem mudou — salve para publicar.
                </span>
            </div>

            <ul class="sortable-list">
                <li
                    v-for="(item, index) in list"
                    :key="item.id"
                    class="sortable-item"
                    :class="{
                        'is-dragging': index === dragIndex,
                        'is-inactive': !item.is_active,
                    }"
                    draggable="true"
                    @dragstart="onDragStart(index, $event)"
                    @dragover.prevent="onDragOver(index)"
                    @dragend="onDragEnd"
                    @drop.prevent
                >
                    <span class="sortable-handle" aria-hidden="true">⠿</span>
                    <div class="sortable-info">
                        <strong class="sortable-title">{{ item.title }}</strong>
                        <span v-if="item.subtitle" class="sortable-subtitle">
                            {{ item.subtitle }}
                        </span>
                    </div>
                    <span
                        class="sortable-badge"
                        :class="item.is_active ? 'is-on' : 'is-off'"
                    >
                        {{ item.is_active ? 'Ativo' : 'Inativo' }}
                    </span>
                    <div class="sortable-actions">
                        <button type="button" class="btn btn-secondary" @click="toggle(item)">
                            {{ item.is_active ? 'Desativar' : 'Ativar' }}
                        </button>
                        <a class="btn btn-secondary" :href="urlFor(urls.edit, item)">Editar</a>
                        <button type="button" class="btn btn-ghost" @click="destroy(item)">
                            Excluir
                        </button>
                    </div>
                </li>
            </ul>
        </template>
    </div>
</template>
