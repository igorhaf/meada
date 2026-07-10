<?php

namespace Database\Seeders;

use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;

class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        // Root: administra a doceria inteira (produtos, kits, encomendas, vitrine).
        User::updateOrCreate(
            ['email' => 'root@sementedoce.com.br'],
            [
                'name' => 'Dona Semente',
                'password' => Hash::make('password'),
                'role' => 'root',
                'phone' => '(11) 98888-0001',
            ]
        );

        // Cliente de demonstração (mostra "Meus pedidos" e "Minhas encomendas").
        User::updateOrCreate(
            ['email' => 'cliente@sementedoce.com.br'],
            [
                'name' => 'Cliente Demo',
                'password' => Hash::make('password'),
                'role' => 'customer',
                'phone' => '(11) 97777-0002',
            ]
        );

        $this->call([
            CategorySeeder::class,
            ProductSeeder::class,
            KitSeeder::class,
            DeliveryZoneSeeder::class,
            BannerSeeder::class,
            SiteSettingSeeder::class,
            PageSeeder::class,
            OrderSeeder::class,
            CustomOrderSeeder::class,
        ]);
    }
}
