import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useContext, useEffect, useMemo, useReducer } from 'react';

/**
 * Sacola do app — mesmo contrato da sacola web (backend/resources/js/stores/cart.js):
 * cada linha tem uma `key` composta ("product-12", "kit-3", "product-12:4-9") para que
 * produtos, kits e combinações de opções coexistam. Persistida em AsyncStorage.
 */

const STORAGE_KEY = 'semente.mobile.cart.v1';

export type CartOption = { id: number; name: string; delta: number };

export type CartLine = {
    key: string;
    id: number;
    type: 'product' | 'kit';
    title: string;
    price: number; // unitário já com deltas de opção
    image: string;
    unit: string;
    minQty: number;
    qty: number;
    options: CartOption[];
    optionsSummary: string;
};

type State = { lines: CartLine[]; hydrated: boolean };

type Action =
    | { type: 'hydrate'; lines: CartLine[] }
    | { type: 'add'; line: Omit<CartLine, 'qty'>; qty: number }
    | { type: 'setQty'; key: string; qty: number }
    | { type: 'remove'; key: string }
    | { type: 'clear' };

function reducer(state: State, action: Action): State {
    switch (action.type) {
        case 'hydrate':
            return { lines: action.lines, hydrated: true };
        case 'add': {
            const existing = state.lines.find((l) => l.key === action.line.key);
            if (existing) {
                return {
                    ...state,
                    lines: state.lines.map((l) =>
                        l.key === action.line.key ? { ...l, qty: Math.min(99, l.qty + action.qty) } : l,
                    ),
                };
            }
            const qty = Math.max(action.line.minQty || 1, action.qty);
            return { ...state, lines: [...state.lines, { ...action.line, qty }] };
        }
        case 'setQty':
            return {
                ...state,
                lines: state.lines.map((l) =>
                    l.key === action.key ? { ...l, qty: Math.max(l.minQty || 1, Math.min(99, action.qty)) } : l,
                ),
            };
        case 'remove':
            return { ...state, lines: state.lines.filter((l) => l.key !== action.key) };
        case 'clear':
            return { ...state, lines: [] };
    }
}

type CartApi = {
    lines: CartLine[];
    count: number;
    subtotal: number;
    addLine: (line: Omit<CartLine, 'qty'>, qty?: number) => void;
    setQty: (key: string, qty: number) => void;
    removeLine: (key: string) => void;
    clear: () => void;
};

const CartContext = createContext<CartApi | null>(null);

export function CartProvider({ children }: { children: React.ReactNode }) {
    const [state, dispatch] = useReducer(reducer, { lines: [], hydrated: false });

    useEffect(() => {
        AsyncStorage.getItem(STORAGE_KEY)
            .then((raw) => dispatch({ type: 'hydrate', lines: raw ? (JSON.parse(raw) as CartLine[]) : [] }))
            .catch(() => dispatch({ type: 'hydrate', lines: [] }));
    }, []);

    useEffect(() => {
        if (state.hydrated) {
            AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(state.lines)).catch(() => {});
        }
    }, [state.hydrated, state.lines]);

    const api = useMemo<CartApi>(
        () => ({
            lines: state.lines,
            count: state.lines.reduce((n, l) => n + l.qty, 0),
            subtotal: state.lines.reduce((s, l) => s + l.price * l.qty, 0),
            addLine: (line, qty = 1) => dispatch({ type: 'add', line, qty }),
            setQty: (key, qty) => dispatch({ type: 'setQty', key, qty }),
            removeLine: (key) => dispatch({ type: 'remove', key }),
            clear: () => dispatch({ type: 'clear' }),
        }),
        [state.lines],
    );

    return <CartContext.Provider value={api}>{children}</CartContext.Provider>;
}

export function useCart(): CartApi {
    const ctx = useContext(CartContext);
    if (!ctx) throw new Error('useCart precisa estar dentro de <CartProvider>.');
    return ctx;
}

/** Chave composta de linha — espelha a regra da sacola web. */
export function lineKey(type: 'product' | 'kit', id: number, optionIds: number[] = []): string {
    const ids = [...optionIds].sort((a, b) => a - b);
    return ids.length ? `${type}-${id}:${ids.join('-')}` : `${type}-${id}`;
}
