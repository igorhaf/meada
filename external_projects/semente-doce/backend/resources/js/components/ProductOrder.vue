<script setup>
import { ref, computed } from 'vue';
import { addLine, brl } from '../stores/cart';

const props = defineProps({
    product: { type: Object, required: true }, // toCartPayload()
    groups: { type: Array, default: () => [] }, // [{id,name,minSelect,maxSelect,required,options:[{id,name,delta}]}]
});

const qty = ref(props.product.minQty || 1);
const justAdded = ref(false);

// Estado de seleção por grupo: single (radio) guarda um id; multi guarda array de ids.
const selection = ref(
    Object.fromEntries(props.groups.map((g) => [g.id, g.maxSelect === 1 ? null : []])),
);

function toggle(group, optionId) {
    if (group.maxSelect === 1) {
        selection.value[group.id] = optionId;
        return;
    }
    const arr = selection.value[group.id];
    const idx = arr.indexOf(optionId);
    if (idx >= 0) arr.splice(idx, 1);
    else if (arr.length < group.maxSelect) arr.push(optionId);
}

function isSelected(group, optionId) {
    const s = selection.value[group.id];
    return group.maxSelect === 1 ? s === optionId : s.includes(optionId);
}

const chosen = computed(() => {
    const out = [];
    for (const g of props.groups) {
        const s = selection.value[g.id];
        const ids = g.maxSelect === 1 ? (s ? [s] : []) : s;
        for (const id of ids) {
            const opt = g.options.find((o) => o.id === id);
            if (opt) out.push({ id: opt.id, groupName: g.name, name: opt.name, delta: opt.delta });
        }
    }
    return out;
});

const deltaTotal = computed(() => chosen.value.reduce((s, o) => s + Number(o.delta || 0), 0));
const unitPrice = computed(() => Number(props.product.price) + deltaTotal.value);

const missingRequired = computed(() =>
    props.groups.filter((g) => g.required && (selection.value[g.id]?.length ?? (selection.value[g.id] ? 1 : 0)) < (g.minSelect || 1)),
);

function add() {
    if (missingRequired.value.length) return;
    const ids = chosen.value.map((o) => o.id).sort((a, b) => a - b);
    const p = props.product;
    addLine(
        {
            key: ids.length ? `product-${p.id}:${ids.join('-')}` : `product-${p.id}`,
            id: p.id,
            type: 'product',
            title: p.title,
            slug: p.slug,
            price: unitPrice.value,
            image: p.image,
            url: p.url,
            unit: p.unit,
            minQty: p.minQty || 1,
            options: chosen.value,
            optionsSummary: chosen.value.map((o) => `${o.groupName}: ${o.name}`).join(' · '),
        },
        qty.value,
    );
    justAdded.value = true;
    setTimeout(() => (justAdded.value = false), 1500);
}
</script>

<template>
    <div class="space-y-5">
        <div v-for="group in groups" :key="group.id" class="rounded-2xl border border-neutral-200 bg-white p-4">
            <div class="mb-2 flex items-center justify-between">
                <h3 class="text-sm font-bold text-neutral-800">{{ group.name }}</h3>
                <span v-if="group.required" class="text-xs font-semibold text-brand-600">Obrigatório</span>
                <span v-else class="text-xs text-neutral-400">Opcional</span>
            </div>
            <p v-if="group.maxSelect > 1" class="mb-2 text-xs text-neutral-500">Escolha até {{ group.maxSelect }}</p>
            <div class="space-y-1.5">
                <label
                    v-for="opt in group.options"
                    :key="opt.id"
                    class="flex cursor-pointer items-center gap-2 rounded-xl border p-2.5 text-sm transition"
                    :class="isSelected(group, opt.id) ? 'border-brand-500 bg-brand-50' : 'border-neutral-200 hover:border-neutral-300'"
                >
                    <input
                        :type="group.maxSelect === 1 ? 'radio' : 'checkbox'"
                        :name="`group-${group.id}`"
                        :checked="isSelected(group, opt.id)"
                        class="text-brand-600 focus:ring-brand-500"
                        @change="toggle(group, opt.id)"
                    />
                    <span class="flex-1 text-neutral-800">{{ opt.name }}</span>
                    <span v-if="Number(opt.delta) > 0" class="text-xs font-semibold text-caramel-600">+{{ brl(opt.delta) }}</span>
                </label>
            </div>
        </div>

        <div class="flex items-center gap-3">
            <span class="text-sm font-medium text-neutral-600">Quantidade</span>
            <div class="inline-flex items-center rounded-full border border-neutral-300">
                <button type="button" class="px-3 py-2 text-lg text-neutral-600 hover:text-brand-700" @click="qty = Math.max(product.minQty || 1, qty - 1)">−</button>
                <span class="w-10 text-center font-semibold">{{ qty }}</span>
                <button type="button" class="px-3 py-2 text-lg text-neutral-600 hover:text-brand-700" @click="qty = Math.min(99, qty + 1)">+</button>
            </div>
            <span v-if="product.minQty > 1" class="text-xs text-neutral-400">mín. {{ product.minQty }}</span>
        </div>

        <button type="button" class="btn-brand w-full text-base" :disabled="missingRequired.length > 0" @click="add">
            {{ justAdded ? 'Na sacola! 🍬' : `Adicionar — ${brl(unitPrice * qty)}` }}
        </button>
        <p v-if="missingRequired.length" class="text-center text-xs text-brand-600">
            Escolha: {{ missingRequired.map((g) => g.name).join(', ') }}
        </p>
    </div>
</template>
