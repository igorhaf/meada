"use client";
import { useState, useEffect } from "react";
import { admin } from "@/lib/admin-api";

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<any[]>([]);
  const [showNew, setShowNew] = useState(false);
  const [form, setForm] = useState({ label: "", project: "", rate_limit: 60 });

  const load = () => admin.get("/keys").then(setKeys).catch(() => {});
  useEffect(() => { load(); }, []);

  async function create() {
    await admin.post("/keys", form);
    setShowNew(false); setForm({ label: "", project: "", rate_limit: 60 }); load();
  }

  async function revoke(id: number) {
    if (!confirm("Revogar esta chave?")) return;
    await admin.del(`/keys/${id}`); load();
  }

  const ic = "w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-lg text-sm text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500";

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">API Keys</h1>
        <button onClick={() => setShowNew(!showNew)} className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-semibold flex items-center gap-2">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" /></svg>
          Nova Key
        </button>
      </div>

      {showNew && (
        <div className="bg-slate-800 border border-slate-700 rounded-xl p-5 space-y-3">
          <div className="grid grid-cols-3 gap-3">
            <div><label className="block text-xs text-slate-400 mb-1">Label</label><input className={ic} placeholder="Ex: Alegria Chat" value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} /></div>
            <div><label className="block text-xs text-slate-400 mb-1">Projeto</label><input className={ic} placeholder="Ex: alegria" value={form.project} onChange={(e) => setForm({ ...form, project: e.target.value })} /></div>
            <div><label className="block text-xs text-slate-400 mb-1">Rate Limit (req/min)</label><input type="number" className={ic} value={form.rate_limit} onChange={(e) => setForm({ ...form, rate_limit: +e.target.value })} /></div>
          </div>
          <div className="flex justify-end gap-2">
            <button onClick={() => setShowNew(false)} className="px-3 py-1.5 text-sm text-slate-400">Cancelar</button>
            <button onClick={create} className="px-4 py-1.5 bg-blue-600 text-white rounded-lg text-sm font-medium">Criar</button>
          </div>
        </div>
      )}

      <div className="bg-slate-800 border border-slate-700 rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead><tr className="border-b border-slate-700"><th className="text-left py-3 px-4 text-xs font-medium text-slate-400 uppercase">Key</th><th className="text-left py-3 px-4 text-xs font-medium text-slate-400 uppercase">Label</th><th className="text-left py-3 px-4 text-xs font-medium text-slate-400 uppercase">Projeto</th><th className="text-left py-3 px-4 text-xs font-medium text-slate-400 uppercase">Rate</th><th className="text-left py-3 px-4 text-xs font-medium text-slate-400 uppercase">Ultimo uso</th><th className="text-right py-3 px-4 text-xs font-medium text-slate-400 uppercase">Acoes</th></tr></thead>
          <tbody className="divide-y divide-slate-700/50">
            {keys.map((k: any) => (
              <tr key={k.id} className="hover:bg-slate-700/30">
                <td className="py-3 px-4 font-mono text-xs text-slate-300">{k.key_masked}</td>
                <td className="py-3 px-4 text-slate-300">{k.label || "-"}</td>
                <td className="py-3 px-4 text-slate-400">{k.project || "-"}</td>
                <td className="py-3 px-4 text-slate-400">{k.rate_limit}/min</td>
                <td className="py-3 px-4 text-xs text-slate-500">{k.last_used_at || "Nunca"}</td>
                <td className="py-3 px-4 text-right">
                  <button onClick={() => revoke(k.id)} className="text-xs text-red-400 hover:text-red-300">Revogar</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
