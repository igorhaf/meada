<script setup>
import { computed, ref } from 'vue';
import { cart, cartSubtotal, cartCount, removeFromCart, setQty, clearCart, brl } from '../stores/cart';

const props = defineProps({
    checkoutUrl: { type: String, required: true },
    loginUrl: { type: String, required: true },
    quoteUrl: { type: String, default: '/api/entrega/cotar' },
    csrf: { type: String, default: '' },
    authenticated: { type: Boolean, default: false },
    buyer: { type: Object, default: () => ({ name: '', email: '', phone: '' }) },
    delivery: {
        type: Object,
        default: () => ({ minOrder: 0, freeAbove: null, defaultFee: 0, pickupEnabled: true, etaMin: 40, etaMax: 70, origin: '' }),
    },
});

// Só type/id/qty/options são enviados; o servidor recalcula preços e taxa.
const itemsPayload = computed(() =>
    cart.items.map((i) => ({ type: i.type, id: i.id, qty: i.qty, options: (i.options || []).map((o) => o.id) })),
);
const itemsJson = computed(() => JSON.stringify(itemsPayload.value));

/* ----------------------------------------------------------- Entrega/Retirada */
const fulfillment = ref('delivery');
const neighborhood = ref('');
const address = ref('');
const quoting = ref(false);
const quoteError = ref('');
const quoted = ref(null); // { fee, eta_min, eta_max, matched }

const freeApplies = computed(
    () => props.delivery.freeAbove != null && cartSubtotal.value >= Number(props.delivery.freeAbove),
);

const deliveryFee = computed(() => {
    if (fulfillment.value === 'pickup') return 0;
    if (freeApplies.value) return 0;
    return quoted.value ? Number(quoted.value.fee) : Number(props.delivery.defaultFee);
});

const total = computed(() => cartSubtotal.value + deliveryFee.value);

const belowMin = computed(
    () => props.delivery.minOrder && cartSubtotal.value < Number(props.delivery.minOrder),
);

async function quote() {
    quoteError.value = '';
    quoted.value = null;
    if (!neighborhood.value.trim()) {
        quoteError.value = 'Informe o seu bairro.';
        return;
    }
    quoting.value = true;
    try {
        const res = await fetch(`${props.quoteUrl}?neighborhood=${encodeURIComponent(neighborhood.value)}`, {
            headers: { Accept: 'application/json' },
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Falha ao consultar a entrega.');
        quoted.value = data;
    } catch (e) {
        quoteError.value = e.message || 'Não foi possível consultar a entrega.';
    } finally {
        quoting.value = false;
    }
}

const canCheckout = computed(() => {
    if (belowMin.value || cartCount.value === 0) return false;
    if (fulfillment.value === 'pickup') return true;
    return neighborhood.value.trim() && address.value.trim();
});
</script>

<template>
    <div v-if="cartCount === 0" class="mx-auto max-w-lg rounded-3xl border border-neutral-200 bg-white p-10 text-center">
        <div class="text-6xl">🧁</div>
        <h2 class="mt-4 text-2xl font-extrabold text-neutral-900">Sua sacola está vazia</h2>
        <p class="mt-2 text-neutral-600">Tem docinho fresquinho esperando por você no cardápio.</p>
        <a href="/" class="btn-brand mt-6">Ver o cardápio</a>
    </div>

    <div v-else class="grid gap-8 lg:grid-cols-3">
        <div class="lg:col-span-2">
            <div class="mb-4 flex items-center justify-between">
                <h1 class="text-2xl font-extrabold text-neutral-900">Sacola <span class="text-base font-medium text-neutral-500">({{ cartCount }} itens)</span></h1>
                <button class="text-sm text-neutral-500 hover:text-red-500" @click="clearCart">Esvaziar</button>
            </div>

            <div class="divide-y divide-neutral-100 overflow-hidden rounded-2xl border border-neutral-200 bg-white">
                <div v-for="item in cart.items" :key="item.key" class="flex gap-4 p-4">
                    <a :href="item.url" class="h-24 w-24 shrink-0 overflow-hidden rounded-xl border border-neutral-200">
                        <img :src="item.image" :alt="item.title" class="h-full w-full object-cover" />
                    </a>
                    <div class="flex min-w-0 flex-1 flex-col">
                        <a :href="item.url" class="font-semibold text-neutral-800 hover:text-brand-700">{{ item.title }}</a>
                        <p v-if="item.optionsSummary" class="text-sm text-neutral-500">{{ item.optionsSummary }}</p>
                        <p v-else class="text-sm text-neutral-400">{{ item.unit }}</p>
                        <div class="mt-auto flex items-center justify-between pt-3">
                            <div class="inline-flex items-center rounded-full border border-neutral-300">
                                <button class="px-3 py-1.5 text-neutral-600 hover:text-brand-700" @click="setQty(item.key, item.qty - 1)">−</button>
                                <span class="w-10 text-center font-semibold">{{ item.qty }}</span>
                                <button class="px-3 py-1.5 text-neutral-600 hover:text-brand-700" @click="setQty(item.key, item.qty + 1)">+</button>
                            </div>
                            <div class="text-right">
                                <div class="font-bold text-neutral-900">{{ brl(item.price * item.qty) }}</div>
                                <button class="text-xs text-neutral-400 hover:text-red-500" @click="removeFromCart(item.key)">Remover</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <aside class="lg:col-span-1">
            <div class="sticky top-24 rounded-2xl border border-neutral-200 bg-white p-6">
                <h2 class="text-lg font-bold text-neutral-900">Resumo</h2>

                <!-- Entrega x Retirada -->
                <div class="mt-4 grid grid-cols-2 gap-2">
                    <button
                        type="button"
                        class="rounded-xl border px-3 py-2.5 text-sm font-semibold transition"
                        :class="fulfillment === 'delivery' ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-neutral-200 text-neutral-600'"
                        @click="fulfillment = 'delivery'"
                    >
                        🛵 Entrega
                    </button>
                    <button
                        v-if="delivery.pickupEnabled"
                        type="button"
                        class="rounded-xl border px-3 py-2.5 text-sm font-semibold transition"
                        :class="fulfillment === 'pickup' ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-neutral-200 text-neutral-600'"
                        @click="fulfillment = 'pickup'"
                    >
                        🏠 Retirar
                    </button>
                </div>

                <div v-if="fulfillment === 'delivery'" class="mt-4 space-y-2">
                    <label class="text-sm font-medium text-neutral-700">Seu bairro</label>
                    <div class="flex gap-2">
                        <input v-model="neighborhood" placeholder="Ex.: Centro" class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none" />
                        <button type="button" class="btn-outline !px-4 !py-2 text-sm" :disabled="quoting" @click="quote">{{ quoting ? '...' : 'OK' }}</button>
                    </div>
                    <input v-model="address" placeholder="Rua, número, complemento" class="w-full rounded-xl border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none" />
                    <p v-if="quoteError" class="text-xs text-red-500">{{ quoteError }}</p>
                    <p v-else-if="quoted" class="text-xs text-pistache-600">
                        Entregamos em {{ neighborhood }} · {{ quoted.eta_min }}–{{ quoted.eta_max }} min
                    </p>
                </div>
                <div v-else class="mt-4 rounded-xl bg-neutral-50 p-3 text-sm text-neutral-600">
                    🏠 Retirar em: <span class="font-medium text-neutral-800">{{ delivery.origin }}</span>
                </div>

                <dl class="mt-4 space-y-2 border-t border-neutral-100 pt-4 text-sm">
                    <div class="flex justify-between"><dt class="text-neutral-600">Subtotal</dt><dd class="font-semibold">{{ brl(cartSubtotal) }}</dd></div>
                    <div class="flex justify-between">
                        <dt class="text-neutral-600">{{ fulfillment === 'pickup' ? 'Retirada' : 'Entrega' }}</dt>
                        <dd class="font-semibold" :class="deliveryFee === 0 ? 'text-pistache-600' : ''">
                            {{ deliveryFee === 0 ? 'Grátis' : brl(deliveryFee) }}
                        </dd>
                    </div>
                </dl>

                <div class="mt-4 flex items-center justify-between border-t border-neutral-200 pt-4">
                    <span class="font-medium text-neutral-600">Total</span>
                    <span class="text-2xl font-extrabold text-neutral-900">{{ brl(total) }}</span>
                </div>

                <p v-if="belowMin" class="mt-3 rounded-xl bg-caramel-100 px-3 py-2 text-xs font-medium text-caramel-700">
                    Pedido mínimo de {{ brl(delivery.minOrder) }}. Faltam {{ brl(delivery.minOrder - cartSubtotal) }}.
                </p>

                <div v-if="!authenticated" class="mt-5">
                    <a :href="loginUrl" class="btn-brand w-full">Entrar para finalizar</a>
                </div>

                <form v-else :action="checkoutUrl" method="POST" class="mt-5 space-y-3">
                    <input type="hidden" name="_token" :value="csrf" />
                    <input type="hidden" name="items" :value="itemsJson" />
                    <input type="hidden" name="fulfillment_type" :value="fulfillment" />
                    <input type="hidden" name="delivery_neighborhood" :value="fulfillment === 'delivery' ? neighborhood : ''" />
                    <input type="hidden" name="delivery_address" :value="fulfillment === 'delivery' ? address : ''" />

                    <div>
                        <label class="mb-1 block text-xs font-medium text-neutral-600">Nome</label>
                        <input name="buyer_name" :value="buyer.name" required class="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none" />
                    </div>
                    <div class="grid grid-cols-2 gap-2">
                        <div>
                            <label class="mb-1 block text-xs font-medium text-neutral-600">E-mail</label>
                            <input name="buyer_email" type="email" :value="buyer.email" required class="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none" />
                        </div>
                        <div>
                            <label class="mb-1 block text-xs font-medium text-neutral-600">WhatsApp</label>
                            <input name="buyer_phone" :value="buyer.phone" placeholder="(00) 00000-0000" class="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none" />
                        </div>
                    </div>
                    <div class="grid grid-cols-2 gap-2">
                        <div>
                            <label class="mb-1 block text-xs font-medium text-neutral-600">Agendar para (opcional)</label>
                            <input name="scheduled_for" type="date" class="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none" />
                        </div>
                        <div>
                            <label class="mb-1 block text-xs font-medium text-neutral-600">Recado</label>
                            <input name="notes" placeholder="Ex.: sem cebola" class="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none" />
                        </div>
                    </div>

                    <button type="submit" class="btn-brand w-full text-base" :disabled="!canCheckout">Ir para o pagamento</button>
                    <p v-if="belowMin" class="text-center text-xs text-neutral-500">Adicione mais itens para atingir o mínimo.</p>
                    <p v-else-if="!canCheckout && fulfillment === 'delivery'" class="text-center text-xs text-neutral-500">Preencha bairro e endereço para continuar.</p>
                </form>
            </div>
        </aside>
    </div>
</template>
