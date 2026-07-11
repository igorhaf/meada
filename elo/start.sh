#!/bin/bash
set -e

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
BACKEND_PID_FILE="/tmp/elo_backend.pid"
FRONTEND_PID_FILE="/tmp/elo_frontend.pid"
BACKEND_PORT=8200
FRONTEND_PORT=3200

red='\033[0;31m'
green='\033[0;32m'
yellow='\033[0;33m'
cyan='\033[0;36m'
nc='\033[0m'

info()    { echo -e "${cyan}ℹ  $1${nc}"; }
success() { echo -e "${green}✔  $1${nc}"; }
warn()    { echo -e "${yellow}⚠  $1${nc}"; }
fail()    { echo -e "${red}✖  $1${nc}"; }

get_pid_on_port() {
    ss -tlnp 2>/dev/null | grep ":$1 " | grep -oP 'pid=\K[0-9]+' | head -1
}

kill_by_pid_file() {
    local pid_file="$1"
    local name="$2"
    if [[ -f "$pid_file" ]]; then
        local pid
        pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null
            sleep 1
            kill -9 "$pid" 2>/dev/null || true
            success "$name parado (PID $pid)"
        else
            warn "$name nao estava rodando (PID $pid stale)"
        fi
        rm -f "$pid_file"
    else
        warn "$name: sem PID file"
    fi
}

stop_backend() {
    kill_by_pid_file "$BACKEND_PID_FILE" "Backend"
    local orphan
    orphan=$(get_pid_on_port $BACKEND_PORT)
    if [[ -n "$orphan" ]]; then
        kill "$orphan" 2>/dev/null
        sleep 1
        kill -9 "$orphan" 2>/dev/null || true
        success "Processo orfao na porta $BACKEND_PORT eliminado"
    fi
}

stop_frontend() {
    kill_by_pid_file "$FRONTEND_PID_FILE" "Frontend"
    local orphan
    orphan=$(get_pid_on_port $FRONTEND_PORT)
    if [[ -n "$orphan" ]]; then
        kill "$orphan" 2>/dev/null
        sleep 1
        kill -9 "$orphan" 2>/dev/null || true
        success "Processo orfao na porta $FRONTEND_PORT eliminado"
    fi
    rm -f "$FRONTEND_DIR/.next/dev/lock" 2>/dev/null
}

start_backend() {
    local existing
    existing=$(get_pid_on_port $BACKEND_PORT)
    if [[ -n "$existing" ]]; then
        success "Backend ja rodando (porta $BACKEND_PORT, PID $existing)"
        echo "$existing" > "$BACKEND_PID_FILE"
        return 0
    fi

    info "Iniciando Backend (FastAPI) na porta $BACKEND_PORT..."
    cd "$BACKEND_DIR"
    source venv/bin/activate
    nohup uvicorn main:app --host 0.0.0.0 --port $BACKEND_PORT > /tmp/elo_backend.log 2>&1 &
    local pid=$!
    echo "$pid" > "$BACKEND_PID_FILE"

    sleep 2
    if kill -0 "$pid" 2>/dev/null; then
        success "Backend iniciado (PID $pid)"
    else
        fail "Backend falhou ao iniciar. Verifique: tail -20 /tmp/elo_backend.log"
        return 1
    fi
}

start_frontend() {
    local existing
    existing=$(get_pid_on_port $FRONTEND_PORT)
    if [[ -n "$existing" ]]; then
        success "Frontend ja rodando (porta $FRONTEND_PORT, PID $existing)"
        echo "$existing" > "$FRONTEND_PID_FILE"
        return 0
    fi

    info "Iniciando Frontend (Next.js) na porta $FRONTEND_PORT..."
    rm -f "$FRONTEND_DIR/.next/dev/lock" 2>/dev/null
    cd "$FRONTEND_DIR"

    # Build if needed
    if [[ ! -d "$FRONTEND_DIR/.next" ]]; then
        info "Building frontend..."
        npm run build > /tmp/elo_frontend_build.log 2>&1
    fi

    nohup npm start -- -p $FRONTEND_PORT > /tmp/elo_frontend.log 2>&1 &
    local pid=$!
    echo "$pid" > "$FRONTEND_PID_FILE"

    sleep 4
    if get_pid_on_port $FRONTEND_PORT > /dev/null 2>&1; then
        success "Frontend iniciado (porta $FRONTEND_PORT)"
    else
        fail "Frontend falhou ao iniciar. Verifique: tail -20 /tmp/elo_frontend.log"
        return 1
    fi
}

status() {
    echo ""
    local bp
    bp=$(get_pid_on_port $BACKEND_PORT)
    if [[ -n "$bp" ]]; then
        success "Backend:  rodando (porta $BACKEND_PORT, PID $bp)"
    else
        fail "Backend:  parado"
    fi

    local fp
    fp=$(get_pid_on_port $FRONTEND_PORT)
    if [[ -n "$fp" ]]; then
        success "Frontend: rodando (porta $FRONTEND_PORT, PID $fp)"
    else
        fail "Frontend: parado"
    fi
    echo ""
}

logs() {
    local target="${1:-all}"
    case "$target" in
        backend)  tail -50 /tmp/elo_backend.log ;;
        frontend) tail -50 /tmp/elo_frontend.log ;;
        *)
            echo "=== Backend ==="
            tail -20 /tmp/elo_backend.log 2>/dev/null || echo "(sem logs)"
            echo ""
            echo "=== Frontend ==="
            tail -20 /tmp/elo_frontend.log 2>/dev/null || echo "(sem logs)"
            ;;
    esac
}

case "${1:-}" in
    start)
        echo ""
        echo -e "${cyan}━━━ Elo Start ━━━${nc}"
        echo ""
        start_backend
        start_frontend
        echo ""
        success "Acesse: http://localhost:$FRONTEND_PORT"
        success "API:    http://localhost:$BACKEND_PORT/v1/messages"
        echo ""
        ;;
    stop)
        echo ""
        echo -e "${cyan}━━━ Elo Stop ━━━${nc}"
        echo ""
        stop_frontend
        stop_backend
        echo ""
        ;;
    restart)
        echo ""
        echo -e "${cyan}━━━ Elo Restart ━━━${nc}"
        echo ""
        stop_frontend
        stop_backend
        sleep 1
        start_backend
        start_frontend
        echo ""
        success "Acesse: http://localhost:$FRONTEND_PORT"
        success "API:    http://localhost:$BACKEND_PORT/v1/messages"
        echo ""
        ;;
    status)
        echo -e "${cyan}━━━ Elo Status ━━━${nc}"
        status
        ;;
    logs)
        logs "$2"
        ;;
    *)
        echo ""
        echo "Uso: $0 {start|stop|restart|status|logs [backend|frontend]}"
        echo ""
        ;;
esac
