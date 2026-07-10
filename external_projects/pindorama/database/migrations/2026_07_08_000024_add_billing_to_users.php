<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Configuração de cobrança da plataforma ao terapeuta (Epic B) — por tenant.
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->decimal('billing_monthly_fee', 10, 2)->default(0)->after('is_verified');
            $table->decimal('billing_discount_percent', 5, 2)->default(0)->after('billing_monthly_fee');
            $table->boolean('billing_free')->default(false)->after('billing_discount_percent'); // gratuidade
            $table->boolean('billing_active')->default(true)->after('billing_free');
            $table->unsignedTinyInteger('billing_day')->default(5)->after('billing_active');
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['billing_monthly_fee', 'billing_discount_percent', 'billing_free', 'billing_active', 'billing_day']);
        });
    }
};
