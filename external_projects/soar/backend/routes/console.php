<?php

use Illuminate\Support\Facades\Schedule;

// Proatividade da família (rodam no container soar-bot, junto do Elo)
Schedule::command('soar:medication-reminders')->everyMinute();
Schedule::command('soar:process-diet-requests')->everyMinute()->withoutOverlapping(10);
Schedule::command('soar:daily-summary')->dailyAt('08:00');
Schedule::command('soar:health-alerts')->dailyAt('09:00');
