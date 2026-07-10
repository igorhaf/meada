<?php

use App\Http\Controllers\Admin;
use App\Http\Controllers\Auth\LoginController;
use App\Http\Controllers\Auth\RegisterController;
use App\Http\Controllers\Auth\SocialController;
use App\Http\Controllers\BookingController;
use App\Http\Controllers\ContactController;
use App\Http\Controllers\HomeController;
use App\Http\Controllers\PageController;
use App\Http\Controllers\PaymentController;
use App\Http\Controllers\PlaceholderController;
use App\Http\Controllers\Professional;
use App\Http\Controllers\ProfessionalController;
use App\Http\Controllers\ProfessionalDirectoryController;
use App\Http\Controllers\AppointmentController;
use App\Http\Controllers\SearchController;
use App\Http\Controllers\ServiceCategoryController;
use App\Http\Controllers\ServiceController;
use Illuminate\Support\Facades\Route;

/* ------------------------------------------------------------- Storefront */
Route::get('/', [HomeController::class, 'index'])->name('home');

Route::get('/busca', [SearchController::class, 'index'])->name('search');
Route::get('/api/busca/sugestoes', [SearchController::class, 'suggest'])->name('search.suggest');

// Agenda: horários livres para um profissional+serviço+local+data (JSON p/ SlotPicker).
Route::get('/api/agenda/horarios', [BookingController::class, 'slots'])->name('booking.slots');

Route::get('/praticas/{serviceCategory:slug}', [ServiceCategoryController::class, 'show'])->name('practices.show');
Route::get('/servico/{service:slug}', [ServiceController::class, 'show'])->name('services.show');
Route::get('/terapeutas', [ProfessionalDirectoryController::class, 'index'])->name('professionals.index');
Route::get('/terapeuta/{user:professional_slug}', [ProfessionalController::class, 'show'])->name('professionals.show');

// Eventos (público)
Route::get('/eventos', [App\Http\Controllers\EventController::class, 'index'])->name('events.index');
Route::get('/evento/{event:slug}', [App\Http\Controllers\EventController::class, 'show'])->name('events.show');

// Institutional pages (content editable by root in the admin).
Route::get('/central-de-ajuda', [PageController::class, 'show'])->defaults('slug', 'central-de-ajuda')->name('pages.help');
Route::get('/trocas-e-devolucoes', [PageController::class, 'show'])->defaults('slug', 'trocas-e-devolucoes')->name('pages.returns');
Route::get('/privacidade', [PageController::class, 'show'])->defaults('slug', 'privacidade')->name('pages.privacy');
Route::get('/contato', [ContactController::class, 'show'])->name('contact.show');
Route::post('/contato', [ContactController::class, 'store'])->name('contact.store');

// Self-contained SVG placeholder image generator (demo assets).
Route::get('/ph', [PlaceholderController::class, 'show'])->name('placeholder');

// Mercado Pago server-to-server webhook (público, isento de CSRF em bootstrap/app.php).
Route::post('/webhooks/mercadopago', [PaymentController::class, 'webhook'])->name('mp.webhook');

/* ------------------------------------------------------------------ Auth */
Route::middleware('guest')->group(function () {
    Route::get('/entrar', [LoginController::class, 'create'])->name('login');
    Route::post('/entrar', [LoginController::class, 'store']);
    Route::get('/cadastrar', [RegisterController::class, 'create'])->name('register');
    Route::post('/cadastrar', [RegisterController::class, 'store']);

    Route::get('/auth/google/redirect', [SocialController::class, 'redirect'])->name('auth.google.redirect');
    Route::get('/auth/google/callback', [SocialController::class, 'callback'])->name('auth.google.callback');
});
Route::post('/sair', [LoginController::class, 'destroy'])->middleware('auth')->name('logout');

/* -------------------------------------------------- Authenticated (paciente) */
Route::middleware('auth')->group(function () {
    // Cria o agendamento (recalcula preço server-side; conflito por profissional).
    Route::post('/agendar', [BookingController::class, 'store'])->name('booking.store');

    // Pagamento (Checkout Transparente / Payment Brick).
    Route::get('/pagamento/{appointment}', [PaymentController::class, 'show'])->name('payment.show');
    Route::post('/pagamento/{appointment}/processar', [PaymentController::class, 'process'])->name('payment.process');

    Route::get('/meus-agendamentos', [AppointmentController::class, 'index'])->name('appointments.index');
    Route::get('/meus-agendamentos/{appointment}', [AppointmentController::class, 'show'])->name('appointments.show');
    Route::post('/meus-agendamentos/{appointment}/pagar', [PaymentController::class, 'retry'])->name('appointments.retry');
    Route::post('/meus-agendamentos/{appointment}/cancelar', [AppointmentController::class, 'cancel'])->name('appointments.cancel');

    Route::middleware('professionals')->group(function () {
        Route::get('/seja-terapeuta', [Professional\OnboardingController::class, 'create'])->name('onboarding.create');
        Route::post('/seja-terapeuta', [Professional\OnboardingController::class, 'store'])->name('onboarding.store');
    });

    // Inscrição em evento + pagamento da inscrição
    Route::post('/evento/{event:slug}/inscrever', [App\Http\Controllers\EventController::class, 'register'])->name('events.register');
    Route::get('/minhas-inscricoes/{registration}', [App\Http\Controllers\EventController::class, 'registration'])->name('events.registration');
    Route::post('/minhas-inscricoes/{registration}/pagar', [App\Http\Controllers\EventController::class, 'pay'])->name('events.registration.pay');
});

/* --------------------------------------------- Painel do terapeuta (tenant) */
Route::middleware(['auth', 'role:professional', 'professionals'])->prefix('painel')->name('professional.')->group(function () {
    Route::get('/', [Professional\DashboardController::class, 'index'])->name('dashboard');

    // Serviços (catálogo do tenant)
    Route::get('/servicos', [Professional\ServiceController::class, 'index'])->name('services.index');
    Route::get('/servicos/novo', [Professional\ServiceController::class, 'create'])->name('services.create');
    Route::post('/servicos', [Professional\ServiceController::class, 'store'])->name('services.store');
    Route::get('/servicos/{service}/editar', [Professional\ServiceController::class, 'edit'])->name('services.edit');
    Route::put('/servicos/{service}', [Professional\ServiceController::class, 'update'])->name('services.update');
    Route::delete('/servicos/{service}', [Professional\ServiceController::class, 'destroy'])->name('services.destroy');
    Route::post('/servicos/{service}/toggle', [Professional\ServiceController::class, 'toggle'])->name('services.toggle');

    // Locais de atendimento (P5)
    Route::get('/locais', [Professional\LocationController::class, 'index'])->name('locations.index');
    Route::get('/locais/novo', [Professional\LocationController::class, 'create'])->name('locations.create');
    Route::post('/locais', [Professional\LocationController::class, 'store'])->name('locations.store');
    Route::get('/locais/{location}/editar', [Professional\LocationController::class, 'edit'])->name('locations.edit');
    Route::put('/locais/{location}', [Professional\LocationController::class, 'update'])->name('locations.update');
    Route::delete('/locais/{location}', [Professional\LocationController::class, 'destroy'])->name('locations.destroy');

    // Disponibilidade semanal (por local) + bloqueios (P6)
    Route::get('/disponibilidade', [Professional\AvailabilityController::class, 'edit'])->name('availability.edit');
    Route::put('/disponibilidade', [Professional\AvailabilityController::class, 'update'])->name('availability.update');
    Route::post('/bloqueios', [Professional\AvailabilityController::class, 'storeBlock'])->name('blocks.store');
    Route::delete('/bloqueios/{block}', [Professional\AvailabilityController::class, 'destroyBlock'])->name('blocks.destroy');

    // Agenda (P9)
    Route::get('/agenda', [Professional\AgendaController::class, 'index'])->name('agenda');
    Route::get('/api/agenda/eventos', [Professional\AgendaController::class, 'events'])->name('agenda.events');
    Route::get('/agendamentos/{appointment}', [Professional\AppointmentController::class, 'show'])->name('appointments.show');
    Route::post('/agendamentos/{appointment}/confirmar', [Professional\AppointmentController::class, 'confirm'])->name('appointments.confirm');
    Route::post('/agendamentos/{appointment}/concluir', [Professional\AppointmentController::class, 'complete'])->name('appointments.complete');
    Route::post('/agendamentos/{appointment}/cancelar', [Professional\AppointmentController::class, 'cancel'])->name('appointments.cancel');

    // Perfil público / branding (P4)
    Route::get('/perfil', [Professional\ProfileController::class, 'edit'])->name('profile.edit');
    Route::put('/perfil', [Professional\ProfileController::class, 'update'])->name('profile.update');

    // Minhas cobranças da plataforma (Epic B)
    Route::get('/cobrancas', [Professional\ChargeController::class, 'index'])->name('charges.index');
    Route::post('/cobrancas/{charge}/pagar', [Professional\ChargeController::class, 'pay'])->name('charges.pay');

    // Eventos do terapeuta (Epic C)
    Route::get('/eventos', [Professional\EventController::class, 'index'])->name('events.index');
    Route::get('/eventos/novo', [Professional\EventController::class, 'create'])->name('events.create');
    Route::post('/eventos', [Professional\EventController::class, 'store'])->name('events.store');
    Route::get('/eventos/{event}/editar', [Professional\EventController::class, 'edit'])->name('events.edit');
    Route::put('/eventos/{event}', [Professional\EventController::class, 'update'])->name('events.update');
    Route::delete('/eventos/{event}', [Professional\EventController::class, 'destroy'])->name('events.destroy');
    Route::get('/eventos/{event}/inscritos', [Professional\EventController::class, 'registrations'])->name('events.registrations');
});

/* --------------------------------------------------- Root admin (site-wide) */
Route::middleware(['auth', 'role:root'])->prefix('admin')->name('admin.')->group(function () {
    Route::get('/', [Admin\DashboardController::class, 'index'])->name('dashboard');

    Route::get('/config', [Admin\SettingsController::class, 'edit'])->name('settings.edit');
    Route::put('/config', [Admin\SettingsController::class, 'update'])->name('settings.update');

    Route::get('/banners', [Admin\BannerController::class, 'index'])->name('banners.index');
    Route::get('/banners/novo', [Admin\BannerController::class, 'create'])->name('banners.create');
    Route::post('/banners', [Admin\BannerController::class, 'store'])->name('banners.store');
    Route::get('/banners/{banner}/editar', [Admin\BannerController::class, 'edit'])->name('banners.edit');
    Route::put('/banners/{banner}', [Admin\BannerController::class, 'update'])->name('banners.update');
    Route::delete('/banners/{banner}', [Admin\BannerController::class, 'destroy'])->name('banners.destroy');

    Route::get('/destaques', [Admin\FeaturedController::class, 'index'])->name('featured');
    Route::post('/destaques/{service}/toggle', [Admin\FeaturedController::class, 'toggle'])->name('featured.toggle');

    // Práticas (categorias de serviço)
    Route::get('/praticas', [Admin\ServiceCategoryController::class, 'index'])->name('practices.index');
    Route::get('/praticas/novo', [Admin\ServiceCategoryController::class, 'create'])->name('practices.create');
    Route::post('/praticas', [Admin\ServiceCategoryController::class, 'store'])->name('practices.store');
    Route::get('/praticas/{serviceCategory}/editar', [Admin\ServiceCategoryController::class, 'edit'])->name('practices.edit');
    Route::put('/praticas/{serviceCategory}', [Admin\ServiceCategoryController::class, 'update'])->name('practices.update');
    Route::delete('/praticas/{serviceCategory}', [Admin\ServiceCategoryController::class, 'destroy'])->name('practices.destroy');

    // Terapeutas (verificação + cobrança/Epic B)
    Route::get('/terapeutas', [Admin\ProfessionalController::class, 'index'])->name('professionals.index');
    Route::get('/terapeutas/{user}', [Admin\ProfessionalController::class, 'show'])->name('professionals.show');
    Route::post('/terapeutas/{user}/verificar', [Admin\ProfessionalController::class, 'toggleVerified'])->name('professionals.verify');
    Route::put('/terapeutas/{user}/cobranca', [Admin\ProfessionalController::class, 'updateBilling'])->name('professionals.billing');
    Route::post('/terapeutas/{user}/cobranca/mensalidade', [Admin\ProfessionalController::class, 'generateMonthly'])->name('professionals.charge.monthly');
    Route::post('/terapeutas/{user}/cobranca', [Admin\ProfessionalController::class, 'createCharge'])->name('professionals.charge.create');
    Route::post('/cobrancas/{charge}/status', [Admin\ProfessionalController::class, 'chargeStatus'])->name('charges.status');

    // Salas do espaço Pindorama (Epic A)
    Route::get('/salas', [Admin\RoomController::class, 'index'])->name('rooms.index');
    Route::post('/salas', [Admin\RoomController::class, 'store'])->name('rooms.store');
    Route::put('/salas/{room}', [Admin\RoomController::class, 'update'])->name('rooms.update');
    Route::delete('/salas/{room}', [Admin\RoomController::class, 'destroy'])->name('rooms.destroy');

    // Aluguel / comissão da plataforma (Epic A)
    Route::get('/comissao', [Admin\CommissionController::class, 'index'])->name('commission.index');
    Route::post('/comissao', [Admin\CommissionController::class, 'store'])->name('commission.store');
    Route::delete('/comissao/{rule}', [Admin\CommissionController::class, 'destroy'])->name('commission.destroy');

    Route::get('/paginas', [Admin\PageController::class, 'index'])->name('pages.index');
    Route::get('/paginas/{page}/editar', [Admin\PageController::class, 'edit'])->name('pages.edit');
    Route::put('/paginas/{page}', [Admin\PageController::class, 'update'])->name('pages.update');

    Route::get('/mensagens', [Admin\MessageController::class, 'index'])->name('messages.index');
    Route::post('/mensagens/{message}/lida', [Admin\MessageController::class, 'toggleRead'])->name('messages.toggle');
    Route::delete('/mensagens/{message}', [Admin\MessageController::class, 'destroy'])->name('messages.destroy');

    Route::get('/pagamentos', [Admin\PaymentController::class, 'index'])->name('payments');
});
