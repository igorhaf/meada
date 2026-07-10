<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Perfil público / branding do terapeuta (landing page própria por profissional).
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->string('professional_slug')->nullable()->unique()->after('professional_name');
            $table->string('headline')->nullable()->after('professional_slug');   // "Acupunturista • Fitoterapeuta"
            $table->text('bio')->nullable()->after('headline');
            $table->string('city')->nullable()->after('bio');
            $table->string('state')->nullable()->after('city');
            $table->string('phone')->nullable()->after('state');
            $table->string('whatsapp')->nullable()->after('phone');
            $table->string('avatar_path')->nullable()->after('whatsapp');
            $table->string('banner_path')->nullable()->after('avatar_path');
            $table->string('brand_primary')->nullable()->after('banner_path');     // hex
            $table->string('brand_secondary')->nullable()->after('brand_primary'); // hex
            $table->string('timezone')->default('America/Sao_Paulo')->after('brand_secondary');
            $table->string('registration_council')->nullable()->after('timezone'); // registro profissional (livre)
            $table->boolean('is_verified')->default(false)->after('registration_council');

            $table->index(['city', 'state']);
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn([
                'professional_slug', 'headline', 'bio', 'city', 'state', 'phone', 'whatsapp',
                'avatar_path', 'banner_path', 'brand_primary', 'brand_secondary',
                'timezone', 'registration_council', 'is_verified',
            ]);
        });
    }
};
