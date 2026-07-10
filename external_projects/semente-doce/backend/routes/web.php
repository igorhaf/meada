<?php

use App\Http\Controllers\Admin;
use App\Http\Controllers\Auth\LoginController;
use App\Http\Controllers\Auth\RegisterController;
use App\Http\Controllers\Auth\SocialController;
use App\Http\Controllers\CartController;
use App\Http\Controllers\CategoryController;
use App\Http\Controllers\CheckoutController;
use App\Http\Controllers\ContactController;
use App\Http\Controllers\CustomOrderController;
use App\Http\Controllers\DeliveryController;
use App\Http\Controllers\HomeController;
use App\Http\Controllers\KitController;
use App\Http\Controllers\OrderController;
use App\Http\Controllers\PageController;
use App\Http\Controllers\PaymentController;
use App\Http\Controllers\PlaceholderController;
use App\Http\Controllers\ProductController;
use App\Http\Controllers\SearchController;
use Illuminate\Support\Facades\Route;

/* ------------------------------------------------------------- Vitrine (loja) */
Route::get('/', [HomeController::class, 'index'])->name('home');

Route::get('/busca', [SearchController::class, 'index'])->name('search');
Route::get('/api/busca/sugestoes', [SearchController::class, 'suggest'])->name('search.suggest');
Route::get('/api/entrega/cotar', [DeliveryController::class, 'quote'])->name('delivery.quote');

Route::get('/carrinho', [CartController::class, 'index'])->name('cart');

Route::get('/produto/{product:slug}', [ProductController::class, 'show'])->name('products.show');
Route::get('/categoria/{category:slug}', [CategoryController::class, 'show'])->name('categories.show');

Route::get('/kits', [KitController::class, 'index'])->name('kits.index');
Route::get('/kit/{kit:slug}', [KitController::class, 'show'])->name('kits.show');

// Encomendas — o cliente pode solicitar sem login (é um pedido de orçamento).
Route::get('/encomendas', [CustomOrderController::class, 'create'])->name('custom-orders.create');
Route::post('/encomendas', [CustomOrderController::class, 'store'])->name('custom-orders.store');
Route::get('/encomendas/obrigado/{customOrder:reference}', [CustomOrderController::class, 'thanks'])->name('custom-orders.thanks');

// Páginas institucionais (conteúdo editável pelo root no painel).
Route::get('/central-de-ajuda', [PageController::class, 'show'])->defaults('slug', 'central-de-ajuda')->name('pages.help');
Route::get('/como-encomendar', [PageController::class, 'show'])->defaults('slug', 'como-encomendar')->name('pages.how');
Route::get('/privacidade', [PageController::class, 'show'])->defaults('slug', 'privacidade')->name('pages.privacy');
Route::get('/contato', [ContactController::class, 'show'])->name('contact.show');
Route::post('/contato', [ContactController::class, 'store'])->name('contact.store');

// Gerador de imagem SVG placeholder auto-contido (assets da demo).
Route::get('/ph', [PlaceholderController::class, 'show'])->name('placeholder');

// Webhook servidor-a-servidor do Mercado Pago (público, isento de CSRF no bootstrap/app.php).
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

/* -------------------------------------------------- Autenticado (cliente) */
Route::middleware('auth')->group(function () {
    Route::post('/checkout', [CheckoutController::class, 'store'])->name('checkout.store');

    // Checkout Transparente (Payment Brick) — pagamento dentro da loja.
    Route::get('/pagamento/{order}', [PaymentController::class, 'show'])->name('payment.show');
    Route::post('/pagamento/{order}/processar', [PaymentController::class, 'process'])->name('payment.process');

    Route::get('/meus-pedidos', [OrderController::class, 'index'])->name('orders.index');
    Route::get('/meus-pedidos/{order}', [OrderController::class, 'show'])->name('orders.show');
    Route::post('/meus-pedidos/{order}/pagar', [PaymentController::class, 'retry'])->name('orders.retry');

    Route::get('/minhas-encomendas', [CustomOrderController::class, 'index'])->name('custom-orders.index');
    Route::get('/minhas-encomendas/{customOrder}', [CustomOrderController::class, 'show'])->name('custom-orders.show');
});

/* --------------------------------------------------- Painel da doceria (root) */
Route::middleware(['auth', 'role:root'])->prefix('painel')->name('admin.')->group(function () {
    Route::get('/', [Admin\DashboardController::class, 'index'])->name('dashboard');

    // Cardápio
    Route::get('/produtos', [Admin\ProductController::class, 'index'])->name('products.index');
    Route::get('/produtos/novo', [Admin\ProductController::class, 'create'])->name('products.create');
    Route::post('/produtos', [Admin\ProductController::class, 'store'])->name('products.store');
    Route::get('/produtos/{product}/editar', [Admin\ProductController::class, 'edit'])->name('products.edit');
    Route::put('/produtos/{product}', [Admin\ProductController::class, 'update'])->name('products.update');
    Route::delete('/produtos/{product}', [Admin\ProductController::class, 'destroy'])->name('products.destroy');
    Route::post('/produtos/{product}/toggle', [Admin\ProductController::class, 'toggle'])->name('products.toggle');

    Route::get('/categorias', [Admin\CategoryController::class, 'index'])->name('categories.index');
    Route::post('/categorias', [Admin\CategoryController::class, 'store'])->name('categories.store');
    Route::put('/categorias/{category}', [Admin\CategoryController::class, 'update'])->name('categories.update');
    Route::delete('/categorias/{category}', [Admin\CategoryController::class, 'destroy'])->name('categories.destroy');

    // ⭐ Kits — o Montador
    Route::get('/kits', [Admin\KitController::class, 'index'])->name('kits.index');
    Route::get('/kits/novo', [Admin\KitController::class, 'create'])->name('kits.create');
    Route::post('/kits', [Admin\KitController::class, 'store'])->name('kits.store');
    Route::get('/kits/{kit}/montar', [Admin\KitController::class, 'edit'])->name('kits.edit');
    Route::put('/kits/{kit}', [Admin\KitController::class, 'update'])->name('kits.update');
    Route::delete('/kits/{kit}', [Admin\KitController::class, 'destroy'])->name('kits.destroy');
    Route::post('/kits/{kit}/toggle', [Admin\KitController::class, 'toggle'])->name('kits.toggle');

    // ⭐ Encomendas — o Kanban
    Route::get('/encomendas', [Admin\CustomOrderController::class, 'index'])->name('custom-orders.index');
    Route::get('/encomendas/{customOrder}', [Admin\CustomOrderController::class, 'show'])->name('custom-orders.show');
    Route::put('/encomendas/{customOrder}/orcar', [Admin\CustomOrderController::class, 'quote'])->name('custom-orders.quote');
    Route::post('/encomendas/{customOrder}/status', [Admin\CustomOrderController::class, 'status'])->name('custom-orders.status');

    // Pedidos (cozinha)
    Route::get('/pedidos', [Admin\OrderController::class, 'index'])->name('orders.index');
    Route::get('/pedidos/{order}', [Admin\OrderController::class, 'show'])->name('orders.show');
    Route::post('/pedidos/{order}/status', [Admin\OrderController::class, 'status'])->name('orders.status');

    // Loja
    Route::get('/config', [Admin\SettingsController::class, 'edit'])->name('settings.edit');
    Route::put('/config', [Admin\SettingsController::class, 'update'])->name('settings.update');

    Route::get('/banners', [Admin\BannerController::class, 'index'])->name('banners.index');
    Route::get('/banners/novo', [Admin\BannerController::class, 'create'])->name('banners.create');
    Route::post('/banners', [Admin\BannerController::class, 'store'])->name('banners.store');
    Route::get('/banners/{banner}/editar', [Admin\BannerController::class, 'edit'])->name('banners.edit');
    Route::put('/banners/{banner}', [Admin\BannerController::class, 'update'])->name('banners.update');
    Route::delete('/banners/{banner}', [Admin\BannerController::class, 'destroy'])->name('banners.destroy');

    Route::get('/destaques', [Admin\FeaturedController::class, 'index'])->name('featured');
    Route::post('/destaques/{product}/toggle', [Admin\FeaturedController::class, 'toggle'])->name('featured.toggle');

    Route::get('/entregas', [Admin\DeliveryZoneController::class, 'index'])->name('delivery-zones.index');
    Route::post('/entregas', [Admin\DeliveryZoneController::class, 'store'])->name('delivery-zones.store');
    Route::put('/entregas/{deliveryZone}', [Admin\DeliveryZoneController::class, 'update'])->name('delivery-zones.update');
    Route::delete('/entregas/{deliveryZone}', [Admin\DeliveryZoneController::class, 'destroy'])->name('delivery-zones.destroy');

    // ⭐ Gestão — insumos, compras, ficha técnica/lucro, gastos e financeiro
    Route::get('/insumos', [Admin\IngredientController::class, 'index'])->name('ingredients.index');
    Route::post('/insumos', [Admin\IngredientController::class, 'store'])->name('ingredients.store');
    Route::put('/insumos/{ingredient}', [Admin\IngredientController::class, 'update'])->name('ingredients.update');
    Route::delete('/insumos/{ingredient}', [Admin\IngredientController::class, 'destroy'])->name('ingredients.destroy');

    Route::get('/compras', [Admin\PurchaseController::class, 'index'])->name('purchases.index');
    Route::get('/compras/nova', [Admin\PurchaseController::class, 'create'])->name('purchases.create');
    Route::post('/compras', [Admin\PurchaseController::class, 'store'])->name('purchases.store');
    Route::get('/compras/{purchase}', [Admin\PurchaseController::class, 'show'])->name('purchases.show');
    Route::delete('/compras/{purchase}', [Admin\PurchaseController::class, 'destroy'])->name('purchases.destroy');

    Route::get('/lucro', [Admin\ProfitController::class, 'index'])->name('profit.index');
    Route::get('/produtos/{product}/ficha', [Admin\RecipeController::class, 'edit'])->name('recipes.edit');
    Route::put('/produtos/{product}/ficha', [Admin\RecipeController::class, 'update'])->name('recipes.update');

    Route::get('/gastos', [Admin\ExpenseController::class, 'index'])->name('expenses.index');
    Route::post('/gastos', [Admin\ExpenseController::class, 'store'])->name('expenses.store');
    Route::put('/gastos/{expense}', [Admin\ExpenseController::class, 'update'])->name('expenses.update');
    Route::delete('/gastos/{expense}', [Admin\ExpenseController::class, 'destroy'])->name('expenses.destroy');

    Route::get('/financeiro', [Admin\FinanceController::class, 'index'])->name('finance.index');

    Route::get('/paginas', [Admin\PageController::class, 'index'])->name('pages.index');
    Route::get('/paginas/{page}/editar', [Admin\PageController::class, 'edit'])->name('pages.edit');
    Route::put('/paginas/{page}', [Admin\PageController::class, 'update'])->name('pages.update');

    Route::get('/mensagens', [Admin\MessageController::class, 'index'])->name('messages.index');
    Route::post('/mensagens/{message}/lida', [Admin\MessageController::class, 'toggleRead'])->name('messages.toggle');
    Route::delete('/mensagens/{message}', [Admin\MessageController::class, 'destroy'])->name('messages.destroy');

    Route::get('/pagamentos', [Admin\PaymentController::class, 'index'])->name('payments');
});
