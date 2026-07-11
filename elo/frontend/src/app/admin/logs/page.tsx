"use client";
import { useState, useEffect } from "react";
import { admin } from "@/lib/admin-api";

export default function LogsPage() {
  const [logs, setLogs] = useState<any[]>([]);
  const [filter, setFilter] = useState({ provider: "", status: "", limit: 50 });

  const load = () => {
    let q = `/logs?limit=${filter.limit}`;
    if (filter.provider) q += `&provider=${filter.provider}`;
    if (filter.status) q += `&status=${filter.status}`;
    admin.get(q).then(setLogs).catch(() => {});
  };
  useEffect(() => { load(); }, [filter]);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Logs de Requisicoes</h1>

      {/* Filters */}
      <div className="flex gap-3">
        <select value={filter.provider} onChange={(e) => setFilter({ ...filter, provider: e.target.value })} className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white">
          <option value="">Todos providers</option>
          <option value="claude_code">Claude Code</option>
          <option value="deepseek">DeepSeek</option>
        </select>
        <select value={filter.status} onChange={(e) => setFilter({ ...filter, status: e.target.value })} className="px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white">
          <option value="">Todos status</option>
          <option value="success">Sucesso</option>
          <option value="fallback">Fallback</option>
          <option value="error">Erro</option>
        </select>
        <button onClick={load} className="px-4 py-2 bg-slate-700 hover:bg-slate-600 text-white rounded-lg text-sm">Atualizar</button>
      </div>

      {/* Table */}
      <div className="bg-slate-800 border border-slate-700 rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead><tr className="border-b border-slate-700">
            <th className="text-left py-3 px-3 text-xs text-slate-400 uppercase">Status</th>
            <th className="text-left py-3 px-3 text-xs text-slate-400 uppercase">Provider</th>
            <th className="text-left py-3 px-3 text-xs text-slate-400 uppercase">Modelo</th>
            <th className="text-left py-3 px-3 text-xs text-slate-400 uppercase">Tokens</th>
            <th className="text-left py-3 px-3 text-xs text-slate-400 uppercase">Latencia</th>
            <th className="text-left py-3 px-3 text-xs text-slate-400 uppercase">Data</th>
          </tr></thead>
          <tbody className="divide-y divide-slate-700/50">
            {logs.map((log: any) => (
              <tr key={log.id} className="hover:bg-slate-700/30">
                <td className="py-2.5 px-3">
                  <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${log.status === "success" ? "bg-green-500/20 text-green-400" : log.status === "fallback" ? "bg-amber-500/20 text-amber-400" : "bg-red-500/20 text-red-400"}`}>{log.status}</span>
                </td>
                <td className="py-2.5 px-3 text-slate-300 text-xs">{log.provider}</td>
                <td className="py-2.5 px-3 text-slate-400 text-xs font-mono">{log.model}</td>
                <td className="py-2.5 px-3 text-slate-400 text-xs">{log.input_tokens + log.output_tokens}</td>
                <td className="py-2.5 px-3 text-slate-400 text-xs">{log.latency_ms}ms</td>
                <td className="py-2.5 px-3 text-slate-500 text-xs">{log.timestamp?.slice(0, 19)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!logs.length && <p className="text-slate-500 text-center py-8 text-sm">Nenhum log registrado</p>}
      </div>
    </div>
  );
}
