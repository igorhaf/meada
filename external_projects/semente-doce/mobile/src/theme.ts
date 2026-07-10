/**
 * Tema Semente Doce — espelho da paleta do site (framboesa + caramelo + pistache + creme).
 * Fonte da verdade das cores: backend/resources/css/app.css (@theme).
 */
export const colors = {
    brand50: '#fff1f4',
    brand100: '#ffe0e7',
    brand200: '#ffc6d4',
    brand300: '#ff9db4',
    brand400: '#fb6690',
    brand500: '#ef3f72',
    brand600: '#d81e5b',
    brand700: '#b3164a',
    brand800: '#8f143f',
    brand900: '#6b0f30',

    caramel100: '#f9ecd6',
    caramel300: '#e6c085',
    caramel400: '#d9a441',
    caramel500: '#c07f24',
    caramel600: '#a3641a',
    caramel700: '#834f16',

    pistache100: '#e8f0d8',
    pistache400: '#9cc06a',
    pistache500: '#7ba24a',
    pistache600: '#5f8039',

    cream: '#fff8f1',
    white: '#ffffff',
    ink: '#262626',
    text: '#404040',
    muted: '#8a8a8a',
    line: '#e8e2da',
    danger: '#dc2626',
} as const;

export const radius = { sm: 10, md: 16, lg: 24, pill: 999 } as const;

/** Escala de espaçamento em múltiplos de 4 (space(4) = 16). */
export const space = (n: number) => n * 4;

export const font = { title: 24, h2: 18, body: 14, small: 12 } as const;
