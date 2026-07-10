<?php

namespace App\Http\Controllers;

use App\Models\DeliveryZone;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class DeliveryController extends Controller
{
    /**
     * Cota a entrega por bairro (alimenta a ilha da sacola). Quando o bairro está
     * na tabela de zonas, devolve a taxa e a janela cadastradas; senão, cai no
     * padrão da config de entrega (taxa e ETA gerais da loja).
     */
    public function quote(Request $request): JsonResponse
    {
        $neighborhood = trim((string) $request->query('neighborhood', ''));

        $zone = DeliveryZone::matchNeighborhood($neighborhood);

        if ($zone) {
            return response()->json([
                'matched' => true,
                'neighborhood' => $zone->neighborhood,
                'fee' => (float) $zone->fee,
                'eta_min' => (int) $zone->eta_min,
                'eta_max' => (int) $zone->eta_max,
            ]);
        }

        return response()->json([
            'matched' => false,
            'neighborhood' => $neighborhood,
            'fee' => (float) config('delivery.default_fee'),
            'eta_min' => (int) config('delivery.eta_min'),
            'eta_max' => (int) config('delivery.eta_max'),
        ]);
    }
}
