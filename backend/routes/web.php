<?php

use Illuminate\Support\Facades\Route;

Route::get('/', fn() => view('pages.home'));
Route::get('/about', fn() => view('pages.about'));
Route::get('/services', fn() => view('pages.services'));
Route::get('/solutions', fn() => view('pages.solutions'));
Route::get('/portfolio', fn() => view('pages.portfolio'));
Route::get('/blog', fn() => view('pages.blog'));
Route::get('/contact', fn() => view('pages.contact'));
