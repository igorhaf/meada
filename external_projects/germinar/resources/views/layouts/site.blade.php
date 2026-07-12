<!doctype html>
<html lang="pt-BR">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>@yield('title', 'Germinar — Gestação, parto e puerpério')</title>
    <meta name="description" content="@yield('description', 'Acompanhamento de gestantes e puérperas, práticas integrativas e formação de doulas — encontros presenciais ou online.')">
    <link rel="icon" type="image/png" href="{{ asset('images/logo-mark.png') }}">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Caprasimo:wght@400&family=Figtree:wght@400;600;700&display=swap" rel="stylesheet">
    @vite('resources/css/site.css')
</head>
<body>
@yield('content')
</body>
</html>
