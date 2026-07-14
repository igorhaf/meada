@extends('layouts.app')

@section('title', 'Pagamento da inscrição')

@section('content')
<div class="container-site py-8"><div class="mx-auto max-w-4xl">
    <a href="{{ route('events.registration', $registration) }}" class="text-sm text-neutral-500 hover:text-brand-700">← Voltar à inscrição</a>
    <h1 class="mb-6 mt-1 text-2xl font-extrabold text-neutral-900">Pagamento seguro</h1>
    <div class="grid gap-6 lg:grid-cols-5">
        <div class="lg:col-span-3">
            <div id="paymentBrick_container"></div>
            <div id="pay-loading" class="rounded-2xl border bg-white p-8 text-center text-sm text-neutral-400">Carregando formas de pagamento…</div>
            <p id="pay-error" class="mt-3 hidden rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700"></p>
            <div id="pix-result" class="hidden rounded-2xl border bg-white p-6 text-center">
                <h2 class="text-lg font-extrabold">Pague com PIX</h2><p class="mt-1 text-sm text-neutral-500">A confirmação ocorrerá automaticamente.</p>
                <img id="pix-qr" alt="QR code PIX" class="mx-auto mt-4 h-56 w-56"><div class="mt-4 flex gap-2"><input id="pix-code" readonly class="w-full rounded-lg border px-3 py-2 text-xs"><button type="button" id="pix-copy" class="btn-outline">Copiar</button></div>
                <a id="pix-link" href="{{ route('events.registration', $registration) }}" class="btn-brand mt-5 inline-flex">Ver inscrição</a>
            </div>
        </div>
        <aside class="lg:col-span-2"><div class="card p-6"><h2 class="font-bold">{{ $registration->event->title }}</h2><p class="mt-2 text-sm text-neutral-500">Inscrição {{ $registration->reference }}</p><div class="mt-4 border-t pt-4 text-lg font-extrabold">{{ money($registration->amount) }}</div></div><p class="mt-3 text-center text-xs text-neutral-400">🔒 Processado pelo Mercado Pago</p></aside>
    </div>
</div></div>
<script src="https://sdk.mercadopago.com/js/v2"></script>
<script>
const mp = new MercadoPago(@json($publicKey), {locale:'pt-BR'}); const errorEl=document.getElementById('pay-error');
const error=(m)=>{errorEl.textContent=m;errorEl.classList.remove('hidden')};
document.getElementById('pix-copy').addEventListener('click',()=>navigator.clipboard?.writeText(document.getElementById('pix-code').value));
mp.bricks().create('payment','paymentBrick_container',{initialization:{amount:{{ (float)$registration->amount }},payer:{email:@json($registration->participant_email)}},customization:{paymentMethods:{creditCard:'all',debitCard:'all',bankTransfer:'all',maxInstallments:12}},callbacks:{onReady:()=>document.getElementById('pay-loading')?.remove(),onError:()=>error('Não foi possível carregar o pagamento.'),onSubmit:({formData})=>fetch(@json(route('events.registration.process', $registration)),{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-TOKEN':@json(csrf_token()),Accept:'application/json'},body:JSON.stringify(formData)}).then(r=>r.json().then(data=>({ok:r.ok,data}))).then(({ok,data})=>{if(!ok)throw new Error(data.error);if(data.qr_code_base64&&!['approved','authorized'].includes(data.status)){document.getElementById('paymentBrick_container').classList.add('hidden');document.getElementById('pix-qr').src='data:image/png;base64,'+data.qr_code_base64;document.getElementById('pix-code').value=data.qr_code||'';document.getElementById('pix-result').classList.remove('hidden')}else window.location.href=data.ticket_url||data.redirect}).catch(e=>{error(e.message||'Falha no pagamento');throw e})}}});
</script>
@endsection
