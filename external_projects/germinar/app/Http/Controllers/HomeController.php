<?php

namespace App\Http\Controllers;

use App\Models\Course;
use App\Models\Practice;
use App\Models\Service;
use App\Models\Setting;
use Illuminate\View\View;

class HomeController extends Controller
{
    public function index(): View
    {
        return view('site.home', [
            'settings' => Setting::all_map(),
            'services' => Service::active()->ordered()->get(),
            'practices' => Practice::active()->ordered()->get(),
            'courses' => Course::active()->ordered()->get(),
        ]);
    }
}
