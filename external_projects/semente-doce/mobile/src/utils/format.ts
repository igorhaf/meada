/** "R$ 1.234,50" — formatador manual (evita depender do Intl do runtime). */
export function brl(value: number): string {
    const fixed = (Math.round(value * 100) / 100).toFixed(2);
    const negative = fixed.startsWith('-');
    const [int, dec] = (negative ? fixed.slice(1) : fixed).split('.');
    const dotted = int.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return `${negative ? '-' : ''}R$ ${dotted},${dec}`;
}
