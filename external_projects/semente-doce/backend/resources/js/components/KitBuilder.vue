<script setup>
import { computed, ref } from 'vue';

// ⭐ Montador de Kits — recurso-estrela do painel.
// Busca itens do cardápio, monta a composição (com snapshot de preço) e mantém
// um <input name="items"> escondido com o JSON que o KitController sincroniza.
const props = defineProps({
    products: { type: Array, default: () => [] }, // {id,title,price,unit,image}
    initialItems: { type: Array, default: () => [] }, // {product_id,label,qty,unit_price}
});

const search = ref('');

// Linhas do kit (estado editável).
const lines = ref(
    (props.initialItems || []).map((it) => ({
        product_id: it.product_id ?? null,
        label: it.label ?? '',
        qty: Number(it.qty) || 1,
        unit_price: Number(it.unit_price) || 0,
    })),
);

const filteredProducts = computed(() => {
    const term = search.value.trim().toLowerCase();
    if (!term) return props.products;
    return props.products.filter((p) => p.title.toLowerCase().includes(term));
});

const total = computed(() =>
    lines.value.reduce((sum, l) => sum + (Number(l.qty) || 0) * (Number(l.unit_price) || 0), 0),
);

function money(v) {
    return 'R$ ' + (Number(v) || 0).toFixed(2).replace('.', ',').replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

function addProduct(p) {
    lines.value.push({
        product_id: p.id,
        label: p.title,
        qty: 1,
        unit_price: Number(p.price) || 0, // snapshot do preço no momento da montagem
    });
}

function addFreeLine() {
    lines.value.push({ product_id: null, label: '', qty: 1, unit_price: 0 });
}

function removeLine(i) {
    lines.value.splice(i, 1);
}

function stepQty(l, delta) {
    l.qty = Math.max(1, (Number(l.qty) || 1) + delta);
}
</script>

<template>
    <div class="grid gap-6 lg:grid-cols-2">
        <!-- Catálogo: escolha os componentes -->
        <div>
            <label class="field-label">Buscar no cardápio</label>
            <input v-model="search" type="text" placeholder="Ex.: brigadeiro, coxinha…" class="field-input" />

            <div class="mt-3 max-h-96 space-y-2 overflow-y-auto pr-1 no-scrollbar">
                <button
                    v-for="p in filteredProducts"
                    :key="p.id"
                    type="button"
                    class="flex w-full items-center gap-3 rounded-2xl border border-neutral-200 bg-white p-2 text-left transition hover:border-brand-300 hover:bg-brand-50"
                    @click="addProduct(p)"
                >
                    <img :src="p.image" alt="" class="h-11 w-11 shrink-0 rounded-xl object-cover" />
                    <span class="min-w-0 flex-1">
                        <span class="line-clamp-1 block text-sm font-medium text-neutral-800">{{ p.title }}</span>
                        <span class="text-xs text-neutral-400">{{ money(p.price) }} · {{ p.unit }}</span>
                    </span>
                    <span class="chip bg-brand-100 text-brand-700">+ Adicionar</span>
                </button>

                <p v-if="filteredProducts.length === 0" class="py-6 text-center text-sm text-neutral-400">
                    Nenhum item encontrado.
                </p>
            </div>

            <button type="button" class="btn-outline mt-3 w-full !py-2 text-sm" @click="addFreeLine">
                + Item avulso (digitado à mão)
            </button>
        </div>

        <!-- Composição do kit -->
        <div class="rounded-3xl bg-neutral-50 p-4">
            <div class="mb-3 flex items-center justify-between">
                <h4 class="font-bold text-neutral-900">🧺 Composição</h4>
                <span class="text-xs text-neutral-400">{{ lines.length }} componente(s)</span>
            </div>

            <div v-if="lines.length === 0" class="rounded-2xl border border-dashed border-neutral-300 py-10 text-center text-sm text-neutral-400">
                Adicione itens do cardápio para montar o kit.
            </div>

            <div v-else class="space-y-2">
                <div v-for="(l, i) in lines" :key="i" class="rounded-2xl border border-neutral-200 bg-white p-3">
                    <div class="flex items-center gap-2">
                        <input
                            v-model="l.label"
                            type="text"
                            placeholder="Nome do componente"
                            class="min-w-0 flex-1 rounded-lg border border-neutral-300 px-2.5 py-1.5 text-sm focus:border-brand-400 focus:outline-none"
                        />
                        <button type="button" class="rounded-lg px-2 py-1.5 text-xs text-red-500 hover:bg-red-50" @click="removeLine(i)">
                            ✕
                        </button>
                    </div>
                    <div class="mt-2 flex items-center justify-between gap-3">
                        <div class="inline-flex items-center rounded-xl border border-neutral-300">
                            <button type="button" class="px-2.5 py-1 text-neutral-600 hover:text-brand-700" @click="stepQty(l, -1)">−</button>
                            <input v-model.number="l.qty" type="number" min="1" class="w-12 border-0 text-center text-sm focus:outline-none" />
                            <button type="button" class="px-2.5 py-1 text-neutral-600 hover:text-brand-700" @click="stepQty(l, 1)">+</button>
                        </div>
                        <label class="flex items-center gap-1 text-xs text-neutral-500">
                            unit. R$
                            <input
                                v-model.number="l.unit_price"
                                type="number"
                                step="0.01"
                                min="0"
                                class="w-20 rounded-lg border border-neutral-300 px-2 py-1 text-sm focus:border-brand-400 focus:outline-none"
                            />
                        </label>
                        <span class="w-24 text-right text-sm font-bold text-neutral-800">
                            {{ money((Number(l.qty) || 0) * (Number(l.unit_price) || 0)) }}
                        </span>
                    </div>
                    <p v-if="l.product_id" class="mt-1 text-[11px] text-neutral-400">↳ do cardápio (preço-snapshot)</p>
                    <p v-else class="mt-1 text-[11px] text-caramel-600">↳ item avulso</p>
                </div>
            </div>

            <!-- Total sugerido -->
            <div class="mt-4 flex items-center justify-between rounded-2xl bg-brand-600 px-4 py-3 text-white">
                <span class="text-sm font-medium">Total dos componentes</span>
                <span class="text-lg font-extrabold">{{ money(total) }}</span>
            </div>
            <p class="mt-2 text-center text-xs text-neutral-400">
                Preço sugerido para o kit — ajuste o <strong>preço do kit</strong> acima para oferecer economia.
            </p>
        </div>

        <!-- Payload submetido junto com o form do kit -->
        <input type="hidden" name="items" :value="JSON.stringify(lines)" />
    </div>
</template>
