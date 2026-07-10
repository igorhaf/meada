# 🍬 Semente Doce — app mobile (Expo / React Native)

Aplicativo do **cliente**: cardápio, kits, sacola e encomendas — consumindo a API JSON do
backend (`/api/v1`). Expo + expo-router + TypeScript, sem libs de UI (tema próprio em
[`src/theme.ts`](src/theme.ts), espelho da paleta do site).

## Rodar

```bash
cd mobile
npm install            # (ou: npx expo install --fix, se alguma versão reclamar)
npx expo start         # abre no Expo Go (QR code) / emulador
```

## Apontando para a API

Por padrão o app usa `extra.apiUrl` do [`app.json`](app.json)
(`http://semente-doce.meadadigital.local/api/v1`) — funciona em emulador rodando **no próprio
host** que resolve o `.local` (Caddy do meada no ar).

**Aparelho físico / emulador Android:** o domínio `.local` não resolve fora do host. Rode o
backend direto e aponte pelo IP da máquina:

```bash
cd ../backend && php artisan serve --host 0.0.0.0 --port 8000
# em outro terminal:
cd ../mobile && EXPO_PUBLIC_API_URL=http://<ip-do-seu-pc>:8000/api/v1 npx expo start
```

## Estrutura

```
app/                    # rotas (expo-router)
├── _layout.tsx         # Stack raiz + CartProvider
├── (tabs)/             # Cardápio · Kits · Encomendar · Sacola · Perfil
├── produto/[slug].tsx  # detalhe do produto (opções + sacola)
└── kit/[slug].tsx      # detalhe do kit
src/
├── api/                # client fetch + tipos (CONTRATO com backend/routes/api.php)
├── store/cart.tsx      # sacola persistida (AsyncStorage), chave composta igual à web
├── components/         # ProductCard, KitCard, QtyStepper…
├── theme.ts            # paleta framboesa/caramelo/pistache
└── utils/format.ts     # brl()
```

## O que o básico faz (e não faz)

- ✅ Navegar cardápio/categorias, detalhe com **opções** (recheio/tamanho…), kits com economia
- ✅ Sacola local persistida + **enviar pedido pelo WhatsApp** da loja
- ✅ Abrir **encomenda** (POST /api/v1/encomendas) com data do evento
- ❌ Login/checkout com pagamento no app — próxima fase (o pagamento MP hoje é o fluxo web)
- ❌ Ícone/splash customizados — próxima fase (usa defaults do Expo)
