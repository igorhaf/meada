{{-- Card de kit reutilizável. Espera $kit (com items carregados). --}}
<div class="card group flex h-full flex-col overflow-hidden">
    <a href="{{ $kit->url }}" class="relative block aspect-square overflow-hidden bg-neutral-100">
        <img
            src="{{ $kit->image_url }}"
            alt="{{ $kit->name }}"
            loading="lazy"
            class="h-full w-full object-cover transition duration-300 group-hover:scale-105"
        >
        <span class="absolute left-2 top-2 chip bg-brand-600 text-white shadow-sm">🎁 Kit {{ $kit->type_label }}</span>
        @if($kit->savings > 0)
            <span class="absolute bottom-2 left-2 chip bg-white/95 text-pistache-600 shadow-sm">Economize {{ money($kit->savings) }}</span>
        @endif
    </a>

    <div class="flex flex-1 flex-col p-3">
        <a href="{{ $kit->url }}" class="line-clamp-2 text-sm font-semibold text-neutral-800 transition hover:text-brand-700">{{ $kit->name }}</a>

        <div class="mt-1 flex flex-wrap items-center gap-x-2 text-xs text-neutral-500">
            @if($kit->serves)<span>🍽️ {{ $kit->serves }}</span>@endif
            <span>· {{ $kit->items->count() }} {{ \Illuminate\Support\Str::plural('item', $kit->items->count()) }}</span>
        </div>

        <div class="mt-auto pt-3">
            @if($kit->savings > 0)
                <p class="text-xs text-neutral-400 line-through">{{ money($kit->components_total) }}</p>
            @endif
            <p class="text-lg font-extrabold text-neutral-900">{{ money($kit->price) }}</p>

            @php($cardProps = ['product' => $kit->toCartPayload(), 'variant' => 'card'])
            <div
                class="mt-3"
                data-island="AddToCart"
                data-props='@json($cardProps)'
            >
                <a href="{{ $kit->url }}" class="btn-outline w-full !py-2 text-sm">Ver kit</a>
            </div>
        </div>
    </div>
</div>
