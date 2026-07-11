"use client";

import { useState, useRef, useEffect } from "react";
import { useRouter } from "next/navigation";
import { getAdminToken } from "@/lib/admin-api";

interface Message {
  id: number;
  text: string;
  sender: "user" | "assistant";
  timestamp: string;
  isStreaming?: boolean;
}

const API = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8200";

export default function ChatPage() {
  const router = useRouter();
  const [authed, setAuthed] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [sessionId, setSessionId] = useState<string>("");
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const token = getAdminToken();
    if (!token) { router.replace("/admin"); return; }
    // Verify token
    fetch(`${API}/api/admin/settings`, { headers: { "x-api-key": token } })
      .then((r) => { if (r.ok) setAuthed(true); else router.replace("/admin"); })
      .catch(() => router.replace("/admin"));
  }, [router]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  async function sendMessage(e: React.FormEvent) {
    e.preventDefault();
    if (!input.trim() || sending) return;
    const text = input.trim();
    setInput("");
    setSending(true);

    const userMsg: Message = { id: Date.now(), text, sender: "user", timestamp: new Date().toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" }) };
    setMessages((prev) => [...prev, userMsg]);

    const botId = Date.now() + 1;
    setMessages((prev) => [...prev, { id: botId, text: "", sender: "assistant", timestamp: "", isStreaming: true }]);

    try {
      const token = getAdminToken();
      const res = await fetch(`${API}/api/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-api-key": token },
        body: JSON.stringify({ message: text, session_id: sessionId || undefined }),
      });

      if (!res.ok) throw new Error("API error");

      const reader = res.body?.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (reader) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          if (line.startsWith("data: ")) {
            try {
              const data = JSON.parse(line.slice(6));
              if (data.text) {
                setMessages((prev) => prev.map((m) => m.id === botId ? { ...m, text: m.text + data.text } : m));
              }
              if (data.session_id) setSessionId(data.session_id);
            } catch {}
          }
        }
      }

      setMessages((prev) => prev.map((m) => m.id === botId ? { ...m, isStreaming: false, timestamp: new Date().toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" }) } : m));
    } catch {
      setMessages((prev) => prev.map((m) => m.id === botId ? { ...m, text: "Erro ao processar. Tente novamente.", isStreaming: false } : m));
    } finally {
      setSending(false);
    }
  }

  if (!authed) return null;

  return (
    <div className="h-screen flex flex-col">
      {/* Header */}
      <header className="h-12 bg-slate-800 border-b border-slate-700 flex items-center px-4 justify-between flex-shrink-0">
        <div className="flex items-center gap-2">
          <svg className="w-5 h-5 text-blue-400" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" /></svg>
          <span className="text-sm font-bold text-white">Elo</span>
          <span className="text-xs text-slate-500">Chat</span>
        </div>
        <button onClick={() => router.push("/admin/dashboard")} className="text-xs text-slate-400 hover:text-white flex items-center gap-1.5 px-3 py-1.5 rounded-lg hover:bg-slate-700 transition-colors">
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18" /></svg>
          Admin
        </button>
      </header>

      {/* Messages */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto p-4 space-y-3">
        {messages.length === 0 && (
          <div className="text-center py-20">
            <div className="w-16 h-16 bg-blue-600/20 rounded-2xl flex items-center justify-center mx-auto mb-4">
              <svg className="w-8 h-8 text-blue-400" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" /></svg>
            </div>
            <h2 className="text-xl font-bold text-white mb-2">Elo</h2>
            <p className="text-slate-400 text-sm">Envie uma mensagem para comecar</p>
          </div>
        )}
        {messages.map((msg) => (
          <div key={msg.id} className={`flex ${msg.sender === "user" ? "justify-end" : "justify-start"}`}>
            <div className={`max-w-[75%] rounded-2xl px-4 py-3 ${msg.sender === "user" ? "bg-blue-600 text-white" : "bg-slate-800 text-slate-200 border border-slate-700"}`}>
              <p className="text-sm whitespace-pre-wrap">{msg.text}{msg.isStreaming && <span className="inline-block w-2 h-4 bg-blue-400 ml-1 animate-pulse rounded-sm" />}</p>
              {msg.timestamp && <p className={`text-[10px] mt-1 ${msg.sender === "user" ? "text-blue-200" : "text-slate-500"}`}>{msg.timestamp}</p>}
            </div>
          </div>
        ))}
      </div>

      {/* Input */}
      <div className="p-4 border-t border-slate-700">
        <form onSubmit={sendMessage} className="flex gap-2 max-w-4xl mx-auto">
          <input type="text" value={input} onChange={(e) => setInput(e.target.value)} placeholder="Digite sua mensagem..." disabled={sending}
            className="flex-1 px-4 py-3 bg-slate-800 border border-slate-700 rounded-xl text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50" />
          <button type="submit" disabled={sending || !input.trim()}
            className="px-5 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-medium transition-colors disabled:opacity-50">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" /></svg>
          </button>
        </form>
      </div>
    </div>
  );
}
