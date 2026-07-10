<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    // Loja única: papéis são só 'customer' (compra/encomenda) e 'root' (a doceria).
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->string('role')->default('customer')->after('email'); // customer | root
            $table->string('phone')->nullable()->after('role');
            $table->string('google_id')->nullable()->unique()->after('phone');
            $table->string('avatar')->nullable()->after('google_id');

            $table->index('role');
        });
    }

    public function down(): void
    {
        Schema::table('users', function (Blueprint $table) {
            $table->dropColumn(['role', 'phone', 'google_id', 'avatar']);
        });
    }
};
