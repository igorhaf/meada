"use client";
import { useState, useEffect } from "react";
import { admin } from "@/lib/admin-api";

const TONES = [
  { value: "formal", label: "Formal" }, { value: "professional", label: "Profissional" },
  { value: "friendly", label: "Amigavel" }, { value: "relaxed", label: "Descontraido" },
  { value: "technical", label: "Tecnico" },
];

const BEHAVIORS = ["proactive", "confirm", "summarize", "channels", "feedback", "memory"];
const RESTRICTIONS = ["personal_data", "discounts", "politics", "medical", "competitors", "contracts"];
const ESCALATIONS = ["explicit", "attempts", "complaint", "highvalue"];

const ic = "w-full px-3 py-2 bg-slate-700 border border-slate-600 rounded-lg text-sm text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500";

export default function PersonasPage() {
  const [personas, setPersonas] = useState<any[]>([]);
  const [editing, setEditing] = useState<any>(null);
  const [preview, setPreview] = useState<string>("");
  const [saved, setSaved] = useState(false);

  const load = () => admin.get("/personas").then(setPersonas).catch(() => {});
  useEffect(() => { load(); }, []);

  async function save() {
    if (!editing) return;
    if (editing.id) { await admin.put(`/personas/${editing.id}`, editing); }
    else { await admin.post("/personas", editing); }
    setSaved(true); setTimeout(() => setSaved(false), 2000);
    load(); setEditing(null);
  }

  async function remove(id: number) {
    if (!confirm("Remover persona?")) return;
    await admin.del(`/personas/${id}`); load();
  }

  async function activate(id: number) {
    await admin.post(`/personas/${id}/activate`); load();
  }

  async function showPreview(id: number) {
    const r = await admin.post(`/personas/${id}/preview`);
    setPreview(r.system_prompt || "");
  }

  function startNew() {
    setEditing({ name: "", description: "", is_active: false, tone: "friendly", response_length: 3, use_emojis: "few", customer_address: "voce", behaviors: [], restrictions: [], escalation_triggers: [], greeting_message: "", closing_message: "", words_to_avoid: [], custom_instructions: "" });
  }

  if (editing) {
    return (
      <div className="space-y-6 max-w-3xl">
        <div className="flex items-center gap-3">
          <button onClick={() => setEditing(null)} className="p-2 hover:bg-slate-700 rounded-lg text-slate-400 hover:text-white">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18" /></svg>
          </button>
          <h1 className="text-2xl font-bold text-white">{editing.id ? "Editar Persona" : "Nova Persona"}</h1>
        </div>
        <div className="bg-slate-800 border border-slate-700 rounded-xl p-6 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div><label className="block text-xs text-slate-400 mb-1">Nome</label><input className={ic} value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} /></div>
            <div><label className="block text-xs text-slate-400 mb-1">Tom</label>
              <select className={ic} value={editing.tone} onChange={(e) => setEditing({ ...editing, tone: e.target.value })}>
                {TONES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </div>
          </div>
          <div><label className="block text-xs text-slate-400 mb-1">Descricao</label><textarea className={ic} rows={2} value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} /></div>
          <div className="grid grid-cols-3 gap-4">
            <div><label className="block text-xs text-slate-400 mb-1">Tamanho resposta</label><input type="range" min="1" max="5" className="w-full" value={editing.response_length} onChange={(e) => setEditing({ ...editing, response_length: +e.target.value })} /><span className="text-xs text-slate-400">{editing.response_length}/5</span></div>
            <div><label className="block text-xs text-slate-400 mb-1">Emojis</label>
              <select className={ic} value={editing.use_emojis} onChange={(e) => setEditing({ ...editing, use_emojis: e.target.value })}>
                <option value="none">Nenhum</option><option value="few">Poucos</option><option value="yes">Sim</option>
              </select>
            </div>
            <div><label className="block text-xs text-slate-400 mb-1">Tratamento</label>
              <select className={ic} value={editing.customer_address} onChange={(e) => setEditing({ ...editing, customer_address: e.target.value })}>
                <option value="voce">Voce</option><option value="senhor">Senhor(a)</option>
              </select>
            </div>
          </div>
          <div><label className="block text-xs text-slate-400 mb-1">Comportamentos</label>
            <div className="flex flex-wrap gap-2">{BEHAVIORS.map((b) => <label key={b} className="flex items-center gap-1.5 text-xs text-slate-300"><input type="checkbox" checked={(editing.behaviors || []).includes(b)} onChange={(e) => { const arr = editing.behaviors || []; setEditing({ ...editing, behaviors: e.target.checked ? [...arr, b] : arr.filter((x: string) => x !== b) }); }} className="rounded border-slate-600" />{b}</label>)}</div>
          </div>
          <div><label className="block text-xs text-slate-400 mb-1">Restricoes</label>
            <div className="flex flex-wrap gap-2">{RESTRICTIONS.map((r) => <label key={r} className="flex items-center gap-1.5 text-xs text-slate-300"><input type="checkbox" checked={(editing.restrictions || []).includes(r)} onChange={(e) => { const arr = editing.restrictions || []; setEditing({ ...editing, restrictions: e.target.checked ? [...arr, r] : arr.filter((x: string) => x !== r) }); }} className="rounded border-slate-600" />{r}</label>)}</div>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div><label className="block text-xs text-slate-400 mb-1">Saudacao</label><input className={ic} value={editing.greeting_message} onChange={(e) => setEditing({ ...editing, greeting_message: e.target.value })} /></div>
            <div><label className="block text-xs text-slate-400 mb-1">Despedida</label><input className={ic} value={editing.closing_message} onChange={(e) => setEditing({ ...editing, closing_message: e.target.value })} /></div>
          </div>
          <div><label className="block text-xs text-slate-400 mb-1">Instrucoes customizadas</label><textarea className={ic} rows={3} value={editing.custom_instructions} onChange={(e) => setEditing({ ...editing, custom_instructions: e.target.value })} /></div>
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-700">
            <button onClick={() => setEditing(null)} className="px-4 py-2 text-sm text-slate-400 hover:text-white">Cancelar</button>
            <button onClick={save} className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-semibold">{saved ? "Salvo!" : "Salvar"}</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">Personas</h1>
        <button onClick={startNew} className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-semibold flex items-center gap-2">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" /></svg>
          Nova Persona
        </button>
      </div>
      <div className="space-y-3">
        {personas.map((p: any) => (
          <div key={p.id} className="bg-slate-800 border border-slate-700 rounded-xl p-4 flex items-center gap-4">
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <h3 className="text-white font-semibold">{p.name}</h3>
                {p.is_default && <span className="px-2 py-0.5 bg-blue-500/20 text-blue-400 rounded text-[10px] font-medium">Padrao</span>}
                {p.is_active && <span className="px-2 py-0.5 bg-green-500/20 text-green-400 rounded text-[10px] font-medium">Ativa</span>}
              </div>
              <p className="text-xs text-slate-400 mt-0.5">{p.description || "Sem descricao"} — Tom: {p.tone}</p>
            </div>
            <button onClick={() => showPreview(p.id)} className="px-3 py-1.5 text-xs text-slate-400 hover:text-white hover:bg-slate-700 rounded-lg">Preview</button>
            <button onClick={() => activate(p.id)} className="px-3 py-1.5 text-xs text-blue-400 hover:bg-blue-600/20 rounded-lg">Ativar</button>
            <button onClick={() => setEditing(p)} className="px-3 py-1.5 text-xs text-slate-400 hover:text-white hover:bg-slate-700 rounded-lg">Editar</button>
            <button onClick={() => remove(p.id)} className="px-3 py-1.5 text-xs text-red-400 hover:bg-red-600/20 rounded-lg">Excluir</button>
          </div>
        ))}
        {!personas.length && <p className="text-slate-500 text-center py-8">Nenhuma persona criada</p>}
      </div>
      {preview && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setPreview("")}>
          <div className="bg-slate-800 border border-slate-700 rounded-xl p-6 max-w-2xl max-h-[80vh] overflow-auto" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-white font-bold mb-3">System Prompt Preview</h3>
            <pre className="text-xs text-slate-300 whitespace-pre-wrap font-mono bg-slate-900 rounded-lg p-4">{preview}</pre>
            <button onClick={() => setPreview("")} className="mt-4 px-4 py-2 bg-slate-700 text-white rounded-lg text-sm">Fechar</button>
          </div>
        </div>
      )}
    </div>
  );
}
