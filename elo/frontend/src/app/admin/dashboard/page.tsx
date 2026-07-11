"use client";
import { useState, useEffect } from "react";
import { admin } from "@/lib/admin-api";

export default function DashboardPage() {
  const [stats, setStats] = useState<any>(null);
  const [providers, setProviders] = useState<any[]>([]);

  useEffect(() => {
    admin.get("/stats").then(setStats).catch(() => {});
    admin.get("/health").then((d: any) => setProviders(d.providers || [])).catch(() => {});
    const i = setInterval(() => {
      admin.get("/stats").then(setStats).catch(() => {});
      admin.get("/health").then((d: any) => setProviders(d.providers || [])).catch(() => {});
    }, 10000);
    return () => clearInterval(i);
  }, []);

  const cards = [
    { label: "Requests Hoje", value: stats?.today || 0, color: "blue" },
    { label: "Total", value: stats?.total_requests || 0, color: "slate" },
    { label: "Taxa Fallback", value: `${stats?.fallback_rate || 0}%`, color: stats?.fallback_rate > 10 ? "amber" : "green" },
    { label: "Latencia Media", value: `${stats?.avg_latency_ms || 0}ms`, color: "purple" },
    { label: "Tokens Hoje", value: (stats?.tokens_today || 0).toLocaleString(), color: "cyan" },
    { label: "Erros", value: stats?.errors || 0, color: stats?.errors > 0 ? "red" : "green" },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Dashboard</h1>

      {/* Metric Cards */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        {cards.map((c) => (
          <div key={c.label} className="bg-slate-800 border border-slate-700 rounded-xl p-4">
            <p className="text-xs text-slate-400 mb-1">{c.label}</p>
            <p className="text-2xl font-bold text-white">{c.value}</p>
          </div>
        ))}
      </div>

      {/* Providers Status */}
      <div className="bg-slate-800 border border-slate-700 rounded-xl p-5">
        <h2 className="text-sm font-semibold text-slate-300 mb-4">Providers</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {providers.map((p: any) => (
            <div key={p.name} className="flex items-center gap-3 bg-slate-700/50 rounded-lg p-3">
              <div className={`w-3 h-3 rounded-full ${p.available ? "bg-green-400" : "bg-red-400"}`} />
              <div className="flex-1">
                <p className="text-sm font-medium text-white">{p.display_name}</p>
                <p className="text-xs text-slate-400">{p.requests} req, {p.successes} ok, {p.failures} err</p>
              </div>
              {p.last_error && <p className="text-xs text-red-400 max-w-32 truncate">{p.last_error}</p>}
            </div>
          ))}
        </div>
      </div>

      {/* Recent Requests */}
      <div className="bg-slate-800 border border-slate-700 rounded-xl p-5">
        <h2 className="text-sm font-semibold text-slate-300 mb-4">Ultimas Requisicoes</h2>
        <div className="space-y-2">
          {(stats?.recent || []).map((r: any) => (
            <div key={r.id} className="flex items-center gap-3 text-xs bg-slate-700/30 rounded-lg px-3 py-2">
              <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${r.status === "success" ? "bg-green-500/20 text-green-400" : r.status === "fallback" ? "bg-amber-500/20 text-amber-400" : "bg-red-500/20 text-red-400"}`}>{r.status}</span>
              <span className="text-slate-400">{r.provider}</span>
              <span className="text-slate-500">{r.model}</span>
              <span className="text-slate-500 ml-auto">{r.latency_ms}ms</span>
              <span className="text-slate-500">{r.input_tokens + r.output_tokens} tok</span>
            </div>
          ))}
          {!(stats?.recent?.length) && <p className="text-slate-500 text-xs text-center py-4">Nenhuma requisicao ainda</p>}
        </div>
      </div>
    </div>
  );
}
