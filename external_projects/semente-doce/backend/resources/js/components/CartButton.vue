<script setup>
import { cartCount, openDrawer } from '../stores/cart';
</script>

<template>
    <button
        type="button"
        class="relative inline-flex items-center gap-2 rounded-full px-3 py-2 text-sm font-medium text-white/90 transition hover:bg-white/10"
        @click="openDrawer"
        aria-label="Abrir sacola"
    >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke-width="1.8" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 10.5V6a3.75 3.75 0 1 0-7.5 0v4.5m11.356-1.993 1.263 12A1.125 1.125 0 0 1 19.75 21H4.25a1.125 1.125 0 0 1-1.12-1.243l1.264-12A1.125 1.125 0 0 1 5.513 6.75h12.974c.576 0 1.059.435 1.119 1.007ZM8.625 10.5a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm7.5 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z" />
        </svg>
        <span class="hidden sm:inline">Sacola</span>
        <span
            v-if="cartCount > 0"
            class="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-caramel-500 px-1.5 text-xs font-bold text-white ring-2 ring-brand-700"
        >
            {{ cartCount }}
        </span>
    </button>
</template>
