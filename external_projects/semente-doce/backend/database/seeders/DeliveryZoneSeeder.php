<?php

namespace Database\Seeders;

use App\Models\DeliveryZone;
use Illuminate\Database\Seeder;

class DeliveryZoneSeeder extends Seeder
{
    /** [bairro, taxa, eta_min, eta_max, ativo] */
    private array $zones = [
        ['Centro', 6.90, 25, 45, true],
        ['Jardins', 12.90, 35, 60, true],
        ['Vila Nova', 9.90, 30, 55, true],
        ['Boa Vista', 8.90, 30, 50, true],
        ['Santa Cecília', 10.90, 35, 60, true],
        ['Bela Vista', 11.90, 35, 65, true],
        ['Jardim Botânico', 15.90, 45, 75, true],
        ['Alto da Colina', 18.90, 50, 80, true],
        ['Distrito Industrial', 20.00, 55, 90, false],
    ];

    public function run(): void
    {
        foreach ($this->zones as $i => [$neighborhood, $fee, $etaMin, $etaMax, $isActive]) {
            DeliveryZone::create([
                'neighborhood' => $neighborhood,
                'fee' => $fee,
                'eta_min' => $etaMin,
                'eta_max' => $etaMax,
                'is_active' => $isActive,
                'position' => $i,
            ]);
        }
    }
}
