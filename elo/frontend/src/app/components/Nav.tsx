"use client";

export default function Nav() {
  return (
    <nav className="border-b border-slate-700 bg-slate-800/50 backdrop-blur">
      <div className="mx-auto flex max-w-7xl items-center justify-center px-4 py-2">
        <span className="text-sm font-bold text-white">Elo</span>
        <span className="ml-2 text-xs text-slate-500">Chat + Persona Config</span>
      </div>
    </nav>
  );
}
