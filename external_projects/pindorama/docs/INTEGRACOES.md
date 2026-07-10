# Integrações — como ativar

As integrações já estão no código. Basta gerar as credenciais (grátis) e colar no
`.env`. Depois: `docker compose exec app php artisan config:clear`.

---

## 💳 Mercado Pago — Checkout Transparente (Payment Brick)

O pagamento acontece **dentro da loja** (formulário de cartão/PIX na nossa página
`/pagamento/{pedido}`), sem redirecionar pro Mercado Pago. Já está configurado no
`.env` com as credenciais de teste.

```env
MP_ENABLED=true
MP_PUBLIC_KEY=TEST-xxxxxxxx        # usada no navegador (Brick) p/ tokenizar o cartão
MP_ACCESS_TOKEN=TEST-xxxxxxxx      # usada no backend p/ criar o pagamento (/v1/payments)
MP_BACK_URL_BASE=                  # base pública (ngrok) p/ o webhook; vazio = APP_URL
```

⚠️ **A Public Key e o Access Token precisam ser da MESMA aplicação** no painel MP —
senão o token do cartão gerado no navegador dá `Card Token not found` no backend.

**Testar:**
- Cartão: Mastercard `5031 4332 1540 6351`, validade futura, CVV `123`, titular **APRO**
  (aprova) ou **OTHE** (recusa). Mais em *MP Developers → Cartões de teste*.
- PIX: funciona no sandbox e gera QR (fica `pendente` até o webhook confirmar).

**Webhook (localhost):** o MP não alcança `localhost`. Para receber a confirmação
automática (essencial p/ PIX/boleto), rode um túnel (ngrok) e aponte
`MP_BACK_URL_BASE=https://seu-tunel.ngrok.io`. O cartão aprova na hora, sem depender
disso.

> Fluxo no código: `CheckoutController` cria o pedido (pendente) → `payment.show`
> renderiza o Brick → `payment.process` chama `MercadoPagoService::createPayment`
> (`/v1/payments`) → atualiza o pedido. O webhook re-consulta o pagamento no MP.

---

## 🔐 Google OAuth (login/cadastro)

1. Acesse **https://console.cloud.google.com/apis/credentials** e crie um projeto.
2. **Tela de consentimento OAuth**: tipo *Externo*, modo *Testing*, e adicione seu
   e-mail em **Usuários de teste**.
3. **Criar credenciais → ID do cliente OAuth → Aplicativo da Web**:
   - **Origens JavaScript autorizadas:** `http://localhost:8095`
   - **URIs de redirecionamento autorizados:** `http://localhost:8095/auth/google/callback`
4. Copie o **Client ID** e **Client Secret** para o `.env`:
   ```env
   GOOGLE_CLIENT_ID=xxxxxxxx.apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=xxxxxxxx
   GOOGLE_REDIRECT_URI="${APP_URL}/auth/google/callback"
   ```
5. O botão **"Continuar com Google"** aparece automaticamente no login e no cadastro
   quando `GOOGLE_CLIENT_ID` está preenchido.

> A URI de redirecionamento no Google **precisa** bater exatamente com `APP_URL`
> (`http://localhost:8095`). Se mudar a porta/domínio, atualize nos dois lugares.
