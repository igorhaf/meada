<?php

namespace App\Http\Controllers;

use App\Models\Kit;
use Illuminate\Contracts\View\View;

class KitController extends Controller
{
    /** Vitrine de kits (cestas montadas pela doceria). */
    public function index(): View
    {
        $kits = Kit::active()
            ->with('items')
            ->orderByDesc('is_featured')
            ->orderBy('position')
            ->get();

        return view('kits.index', compact('kits'));
    }

    /** Página de um kit, com os componentes que vêm dentro dele. */
    public function show(Kit $kit): View
    {
        abort_unless($kit->is_active, 404);

        $kit->load('items.product');

        return view('kits.show', compact('kit'));
    }
}
