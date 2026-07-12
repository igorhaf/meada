<?php

use App\Http\Controllers\Admin\CourseController;
use App\Http\Controllers\Admin\PracticeController;
use App\Http\Controllers\Admin\ServiceController;
use App\Http\Controllers\Admin\SettingController;
use App\Http\Controllers\Auth\LoginController;
use App\Http\Controllers\HomeController;
use Illuminate\Support\Facades\Route;

/* ---------------------------------------------------------------- Público */
Route::get('/', [HomeController::class, 'index'])->name('home');

/* ------------------------------------------------------------------- Auth */
Route::middleware('guest')->group(function () {
    Route::get('/entrar', [LoginController::class, 'create'])->name('login');
    Route::post('/entrar', [LoginController::class, 'store'])
        ->middleware('throttle:5,1')->name('login.store');
});
Route::post('/sair', [LoginController::class, 'destroy'])
    ->middleware('auth')->name('logout');

/* ------------------------------------------------------------------ Admin */
Route::prefix('admin')->name('admin.')->middleware('auth')->group(function () {
    Route::redirect('/', '/admin/configuracoes')->name('dashboard');

    // Configurações do site (textos das seções, contato, imagens)
    Route::get('/configuracoes', [SettingController::class, 'edit'])->name('settings.edit');
    Route::put('/configuracoes', [SettingController::class, 'update'])->name('settings.update');

    // Reordenação via Vue (drag & drop) — declaradas ANTES dos resources
    // para não colidir com {parâmetro} das rotas resource.
    Route::post('/servicos/reordenar', [ServiceController::class, 'reorder'])->name('servicos.reorder');
    Route::post('/praticas/reordenar', [PracticeController::class, 'reorder'])->name('praticas.reorder');
    Route::post('/cursos/reordenar', [CourseController::class, 'reorder'])->name('cursos.reorder');

    // Ativar/desativar item sem sair da lista (Vue)
    Route::patch('/servicos/{service}/ativo', [ServiceController::class, 'toggle'])->name('servicos.toggle');
    Route::patch('/praticas/{practice}/ativo', [PracticeController::class, 'toggle'])->name('praticas.toggle');
    Route::patch('/cursos/{course}/ativo', [CourseController::class, 'toggle'])->name('cursos.toggle');

    // CRUDs das seções da home
    Route::resource('servicos', ServiceController::class)
        ->parameters(['servicos' => 'service'])->except(['show']);
    Route::resource('praticas', PracticeController::class)
        ->parameters(['praticas' => 'practice'])->except(['show']);
    Route::resource('cursos', CourseController::class)
        ->parameters(['cursos' => 'course'])->except(['show']);
});
