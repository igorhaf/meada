import { reactive, computed, watch } from 'vue';

const STORAGE_KEY = 'semente.cart.v1';

function load() {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        return raw ? JSON.parse(raw) : [];
    } catch {
        return [];
    }
}

// Estado reativo único e compartilhado — toda ilha importa a mesma instância.
// Cada linha tem uma `key` composta (ex.: "product-12", "kit-3", "product-12:4-9")
// para que produtos, kits e combinações de opções coexistam na sacola.
export const cart = reactive({
    items: load(),
    drawerOpen: false,
});

export const cartCount = computed(() => cart.items.reduce((n, i) => n + i.qty, 0));
export const cartSubtotal = computed(() => cart.items.reduce((s, i) => s + i.price * i.qty, 0));

/**
 * Adiciona (ou incrementa) uma linha já montada. `line.key` identifica a linha.
 */
export function addLine(line, qty = 1) {
    const existing = cart.items.find((i) => i.key === line.key);
    if (existing) {
        existing.qty += qty;
    } else {
        cart.items.push({ ...line, qty: Math.max(qty, line.minQty || 1) });
    }
    cart.drawerOpen = true;
}

export function removeFromCart(key) {
    cart.items = cart.items.filter((i) => i.key !== key);
}

export function setQty(key, qty) {
    const item = cart.items.find((i) => i.key === key);
    if (item) item.qty = Math.max(item.minQty || 1, Math.min(99, qty));
}

export function clearCart() {
    cart.items = [];
}

export const openDrawer = () => (cart.drawerOpen = true);
export const closeDrawer = () => (cart.drawerOpen = false);

// Persiste + mantém as abas em sincronia.
watch(
    () => cart.items,
    (value) => localStorage.setItem(STORAGE_KEY, JSON.stringify(value)),
    { deep: true },
);

window.addEventListener('storage', (e) => {
    if (e.key === STORAGE_KEY) cart.items = load();
});

export const brl = (value) =>
    Number(value || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
