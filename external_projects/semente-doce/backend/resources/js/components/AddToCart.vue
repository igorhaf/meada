<script setup>
import { ref } from 'vue';
import { addLine } from '../stores/cart';

const props = defineProps({
    product: { type: Object, required: true }, // toCartPayload()
    variant: { type: String, default: 'card' }, // 'card' | 'full'
    orderUrl: { type: String, default: '' }, // link p/ encomenda quando sob encomenda
});

const qty = ref(props.product.minQty || 1);
const justAdded = ref(false);

function lineFromProduct() {
    const p = props.product;
    return {
        key: `${p.type}-${p.id}`,
        id: p.id,
        type: p.type,
        title: p.title,
        slug: p.slug,
        price: p.price,
        image: p.image,
        url: p.url,
        unit: p.unit,
        minQty: p.minQty || 1,
        options: [],
        optionsSummary: '',
    };
}

function add() {
    addLine(lineFromProduct(), props.variant === 'full' ? qty.value : props.product.minQty || 1);
    justAdded.value = true;
    setTimeout(() => (justAdded.value = false), 1500);
}
</script>

<template>
    <!-- Sob encomenda: manda para o formulário de encomenda -->
    <a v-if="product.madeToOrder" :href="orderUrl || product.url" class="btn-caramel w-full !py-2.5 text-sm">
        🎂 Encomendar
    </a>

    <!-- Tem opções a escolher: manda para a página do produto -->
    <a v-else-if="product.needsChoice" :href="product.url" class="btn-outline w-full !py-2.5 text-sm">
        Escolher opções
    </a>

    <!-- Card compacto: adiciona direto -->
    <button
        v-else-if="variant === 'card'"
        type="button"
        class="btn-brand w-full !py-2.5 text-sm"
        @click.prevent="add"
    >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.836l.383 1.437M7.5 14.25a3 3 0 0 0-3 3h15.75m-12.75-3h11.218c1.121-.001 2.09-.775 2.337-1.87l1.35-6a1.125 1.125 0 0 0-1.1-1.38H5.106M7.5 14.25 5.106 5.272M6 20.25a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Zm12.75 0a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Z" />
        </svg>
        <span>{{ justAdded ? 'Na sacola!' : 'Adicionar' }}</span>
    </button>

    <!-- Controle completo (página de kit / item pronta-entrega sem opções) -->
    <div v-else class="space-y-4">
        <div class="flex items-center gap-3">
            <span class="text-sm font-medium text-neutral-600">Quantidade</span>
            <div class="inline-flex items-center rounded-full border border-neutral-300">
                <button type="button" class="px-3 py-2 text-lg text-neutral-600 hover:text-brand-700" @click="qty = Math.max(product.minQty || 1, qty - 1)">−</button>
                <span class="w-10 text-center font-semibold">{{ qty }}</span>
                <button type="button" class="px-3 py-2 text-lg text-neutral-600 hover:text-brand-700" @click="qty = Math.min(99, qty + 1)">+</button>
            </div>
        </div>

        <button type="button" class="btn-brand w-full text-base" @click="add">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.836l.383 1.437M7.5 14.25a3 3 0 0 0-3 3h15.75m-12.75-3h11.218c1.121-.001 2.09-.775 2.337-1.87l1.35-6a1.125 1.125 0 0 0-1.1-1.38H5.106M7.5 14.25 5.106 5.272M6 20.25a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Zm12.75 0a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Z" />
            </svg>
            {{ justAdded ? 'Na sacola!' : 'Adicionar à sacola' }}
        </button>
    </div>
</template>
