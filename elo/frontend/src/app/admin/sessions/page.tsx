"use client";
import { useState, useEffect } from "react";
import { admin } from "@/lib/admin-api";

export default function SessionsPage() {
  const [data, setData] = useState<any>({ sessions: {}, count: 0 });
  const load = () => admin.get("/sessions").then(setData).catch(() => {});
  useEffect(() => { load(); }, []);

  async function removeSession(key: string) {
    await admin.del(`/sessions/${key}`); load();
  }

  async function clearAll() {
    if (!confirm(`Limpar ${data.count} sessoes?`)) return;
    await admin.del("/sessions"); load();
  }

  const entries = Object.entries(data.sessions || {});

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Sessoes</h1>
          <p className="text-sm text-slate-400 mt-1">{data.count} sessoes ativas</p>
        </div>
        {data.count > 0 && (
          <button onClick={clearAll} className="px-4 py-2 bg-red-600/20 text-red-400 hover:bg-red-600/30 rounded-lg text-sm font-medium">Limpar todas</button>
        )}
      </div>
      <div className="bg-slate-800 border border-slate-700 rounded-xl overflow-hidden">
        {entries.length > 0 ? (
          <table className="w-full text-sm">
            <thead><tr className="border-b border-slate-700"><th className="text-left py-3 px-4 text-xs text-slate-400 uppercase">Conversation Key</th><th className="text-left py-3 px-4 text-xs text-slate-400 uppercase">Session ID</th><th className="text-right py-3 px-4 text-xs text-slate-400 uppercase">Acoes</th></tr></thead>
            <tbody className="divide-y divide-slate-700/50">
              {entries.map(([key, sid]) => (
                <tr key={key} className="hover:bg-slate-700/30">
                  <td className="py-3 px-4 font-mono text-xs text-slate-300">{String(key).slice(0, 20)}...</td>
                  <td className="py-3 px-4 font-mono text-xs text-slate-400">{String(sid).slice(0, 30)}...</td>
                  <td className="py-3 px-4 text-right"><button onClick={() => removeSession(key)} className="text-xs text-red-400 hover:text-red-300">Remover</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p className="text-slate-500 text-center py-8 text-sm">Nenhuma sessao ativa</p>
        )}
      </div>
    </div>
  );
}
