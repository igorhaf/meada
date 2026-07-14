#!/bin/sh
set -eu

# A aplicação possui um único container PHP por ambiente. Este processo PID 1
# mantém FPM e scheduler vivos e encaminha o encerramento para os dois.
php artisan schedule:work --no-interaction &
scheduler_pid=$!

php-fpm &
fpm_pid=$!

stop_children() {
    kill -TERM "$scheduler_pid" "$fpm_pid" 2>/dev/null || true
    wait "$scheduler_pid" "$fpm_pid" 2>/dev/null || true
}

trap stop_children INT TERM EXIT
wait "$fpm_pid"
