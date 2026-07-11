"use client";
import { useState, useEffect } from "react";
import { admin } from "@/lib/admin-api";

export default function ModelsPage() {
  const [models, setModels] = useState<any[]>([]);
  useEffect(() => { admin.get("/models").then(setModels).catch(() => {}); }, []);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Modelos</h1>
      <div className="bg-slate-800 border border-slate-700 rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead><tr className="border-b border-slate-700"><th className="text-left py-3 px-4 text-xs font-medium text-slate-400 uppercase">Modelo</th><th className="text-left py-3 px-4 text-xs font-medium text-slate-400 uppercase">Alias CLI</th><th className="text-left py-3 px-4 text-xs font-medium text-slate-400 uppercase">Max Tokens</th><th className="text-left py-3 px-4 text-xs font-medium text-slate-400 uppercase">Timeout</th><th className="text-left py-3 px-4 text-xs font-medium text-slate-400 uppercase">Fallback DeepSeek</th></tr></thead>
          <tbody className="divide-y divide-slate-700/50">
            {models.map((m: any) => (
              <tr key={m.id} className="hover:bg-slate-700/30">
                <td className="py-3 px-4 font-mono text-white text-xs">{m.id}</td>
                <td className="py-3 px-4 text-slate-300">{m.alias}</td>
                <td className="py-3 px-4 text-slate-300">{m.max_tokens?.toLocaleString()}</td>
                <td className="py-3 px-4 text-slate-300">{m.timeout}s</td>
                <td className="py-3 px-4 text-slate-400 font-mono text-xs">{m.deepseek_map || "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
