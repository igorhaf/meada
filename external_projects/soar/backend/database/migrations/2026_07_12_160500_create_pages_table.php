<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('pages', function (Blueprint $table) {
            $table->id();
            $table->foreignId('parent_id')->nullable()->constrained('pages')->cascadeOnDelete();
            $table->foreignId('owner_id')->nullable()->constrained('users')->cascadeOnDelete();
            $table->string('scope', 20)->default('personal'); // shared | personal
            $table->string('title');
            $table->string('icon', 16)->nullable();
            $table->longText('content')->nullable();
            $table->unsignedInteger('position')->default(0);
            $table->timestamps();

            $table->index(['scope', 'parent_id', 'position']);
            $table->index(['owner_id', 'parent_id', 'position']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('pages');
    }
};
