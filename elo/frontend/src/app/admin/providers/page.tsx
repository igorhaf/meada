"use client";
import { useState, useEffect } from "react";
import { admin } from "@/lib/admin-api";

export default function ProvidersPage() {
  const [providers, setProviders] = useState<any[]>([]);
  const [testing, setTesting] = useState<string>("");

  const load = () => admin.get("/providers").then(setProviders).catch(() => {});
  useEffect(() => { load(); }, []);

  async function toggle(name: string, enabled: boolean) {
    await admin.put(`/providers/${name}`, { is_enabled: enabled ? 1 : 0 });
    load();
  }

  async function testProvider(name: string) {
    setTesting(name);
    try {
      const r = await admin.post(`/providers/${name}/test`);
      alert(r.success ? `OK: "${r.text}"` : `Falha: ${r.error}`);
    } catch (e: any) { alert(`Erro: ${e.message}`); }
    finally { setTesting(""); }
  }

  async function updateKey(name: string) {
    const key = prompt("Nova API Key:");
    if (key !== null) { await admin.put(`/providers/${name}`, { api_key: key }); load(); }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Providers</h1>
      <div className="space-y-3">
        {providers.map((p: any) => (
          <div key={p.name} className="bg-slate-800 border border-slate-700 rounded-xl p-5">
            <div className="flex items-center gap-4 mb-4">
              <div className={`w-3 h-3 rounded-full ${p.available ? "bg-green-400" : "bg-red-400"}`} />
              <div className="flex-1">
                <h3 className="text-white font-semibold">{p.display_name}</h3>
                <p className="text-xs text-slate-400">Prioridade: {p.priority} | {p.requests || 0} requests</p>
              </div>
              <button onClick={() => toggle(p.name, !p.is_enabled)} className={`px-3 py-1 rounded-lg text-xs font-medium ${p.is_enabled ? "bg-green-500/20 text-green-400" : "bg-slate-600 text-slate-400"}`}>
                {p.is_enabled ? "Ativo" : "Inativo"}
              </button>
              <button onClick={() => testProvider(p.name)} disabled={testing === p.name} className="px-3 py-1 bg-blue-600/20 text-blue-400 rounded-lg text-xs font-medium hover:bg-blue-600/30 disabled:opacity-50">
                {testing === p.name ? "Testando..." : "Testar"}
              </button>
            </div>
            <div className="grid grid-cols-4 gap-3 text-center">
              <div className="bg-slate-700/50 rounded-lg p-2">
                <p className="text-lg font-bold text-white">{p.successes || 0}</p>
                <p className="text-[10px] text-slate-400">Sucesso</p>
              </div>
              <div className="bg-slate-700/50 rounded-lg p-2">
                <p className="text-lg font-bold text-white">{p.failures || 0}</p>
                <p className="text-[10px] text-slate-400">Falhas</p>
              </div>
              <div className="bg-slate-700/50 rounded-lg p-2">
                <p className="text-lg font-bold text-white">{p.fallbacks_triggered || 0}</p>
                <p className="text-[10px] text-slate-400">Fallbacks</p>
              </div>
              <div className="bg-slate-700/50 rounded-lg p-2 cursor-pointer hover:bg-slate-700" onClick={() => updateKey(p.name)}>
                <p className="text-lg font-bold text-white">{p.api_key ? "***" : "-"}</p>
                <p className="text-[10px] text-slate-400">API Key</p>
              </div>
            </div>
            {p.last_error && <p className="text-xs text-red-400 mt-3 bg-red-500/10 rounded-lg px-3 py-2">{p.last_error}</p>}
          </div>
        ))}
      </div>
    </div>
  );
}
