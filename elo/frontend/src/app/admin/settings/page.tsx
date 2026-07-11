"use client";
import { useState, useEffect } from "react";
import { admin } from "@/lib/admin-api";

const SETTINGS_SCHEMA = [
  { key: "admin_password", label: "Senha Admin", type: "password", desc: "Senha para acessar o painel administrativo" },
  { key: "default_model", label: "Modelo Padrao", type: "select", options: ["claude-sonnet-4-6", "claude-haiku-4-5", "claude-opus-4-6"], desc: "Modelo usado quando nao especificado" },
  { key: "log_level", label: "Nivel de Log", type: "select", options: ["DEBUG", "INFO", "WARNING", "ERROR"], desc: "Detalhamento dos logs do sistema" },
  { key: "cors_origins", label: "CORS Origins", type: "text", desc: "Origens permitidas (* = todas)" },
  { key: "large_arg_threshold", label: "Threshold Args (bytes)", type: "number", desc: "Limite para envio via stdin em vez de argumento CLI" },
];

export default function SettingsPage() {
  const [settings, setSettings] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(false);

  useEffect(() => { admin.get("/settings").then(setSettings).catch(() => {}); }, []);

  async function save() {
    await admin.put("/settings", settings);
    setSaved(true); setTimeout(() => setSaved(false), 2000);
  }

  const ic = "w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-lg text-sm text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500";

  return (
    <div className="space-y-6 max-w-2xl">
      <h1 className="text-2xl font-bold text-white">Configuracoes</h1>
      <div className="bg-slate-800 border border-slate-700 rounded-xl p-6 space-y-5">
        {SETTINGS_SCHEMA.map((s) => (
          <div key={s.key}>
            <label className="block text-sm font-medium text-slate-300 mb-1">{s.label}</label>
            <p className="text-xs text-slate-500 mb-2">{s.desc}</p>
            {s.type === "select" ? (
              <select className={ic} value={settings[s.key] || ""} onChange={(e) => setSettings({ ...settings, [s.key]: e.target.value })}>
                {s.options?.map((o) => <option key={o} value={o}>{o}</option>)}
              </select>
            ) : (
              <input type={s.type || "text"} className={ic} value={settings[s.key] || ""} onChange={(e) => setSettings({ ...settings, [s.key]: e.target.value })} />
            )}
          </div>
        ))}
        <div className="flex justify-end pt-4 border-t border-slate-700">
          <button onClick={save} className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-semibold">
            {saved ? "Salvo!" : "Salvar Configuracoes"}
          </button>
        </div>
      </div>
    </div>
  );
}
