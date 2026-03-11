'use client';

import Navbar from '../components/Navbar';
import Footer from '../components/Footer';

const openings = [
  {
    role: 'Desenvolvedor Full Stack',
    type: 'Remoto · CLT ou PJ',
    area: 'Engenharia',
    color: '#3b82f6',
    desc: 'Buscamos dev full stack com experiência em React, Next.js e Node.js para atuar em projetos de alto impacto para nossos clientes.',
  },
  {
    role: 'Designer UI/UX',
    type: 'Remoto · PJ',
    area: 'Design',
    color: '#8b5cf6',
    desc: 'Procuramos designer apaixonado por interfaces digitais, com portfólio sólido e domínio de Figma.',
  },
  {
    role: 'Especialista em IA',
    type: 'Remoto · PJ',
    area: 'Inteligência Artificial',
    color: '#06b6d4',
    desc: 'Vaga para profissional com experiência em LLMs, automações com n8n/Zapier e integração de APIs de IA.',
  },
  {
    role: 'Gerente de Projetos',
    type: 'Híbrido · CLT',
    area: 'Operações',
    color: '#10b981',
    desc: 'Procuramos PM experiente para coordenar projetos digitais, fazer a ponte entre clientes e equipe técnica.',
  },
];

const perks = [
  { emoji: '🌎', title: '100% Remoto', desc: 'Trabalhe de onde quiser. Focamos em resultado, não em presença.' },
  { emoji: '🚀', title: 'Projetos reais', desc: 'Você vai trabalhar em projetos que chegam ao mercado, com clientes reais.' },
  { emoji: '📚', title: 'Aprendizado contínuo', desc: 'Budget mensal para cursos, livros e eventos da área.' },
  { emoji: '🤝', title: 'Time pequeno e focado', desc: 'Sem burocracia. Aqui sua voz tem peso desde o primeiro dia.' },
  { emoji: '💰', title: 'Remuneração competitiva', desc: 'Pagamos bem e com transparência. Sem tabelas secretas.' },
  { emoji: '⚡', title: 'Cultura de alto desempenho', desc: 'Gostamos de gente que faz acontecer e cresce junto com a empresa.' },
];

export default function CarreirasPage() {
  return (
    <div style={{ background: '#000812', minHeight: '100vh' }}>
      <Navbar />

      {/* Hero */}
      <section style={{ paddingTop: '8rem', paddingBottom: '5rem', textAlign: 'center', position: 'relative', overflow: 'hidden' }}>
        <div style={{ position: 'absolute', top: '20%', left: '50%', transform: 'translateX(-50%)', width: '600px', height: '300px', background: 'radial-gradient(ellipse, rgba(99,102,241,0.12) 0%, transparent 70%)', pointerEvents: 'none' }} />
        <div style={{ maxWidth: '800px', margin: '0 auto', padding: '0 2rem', position: 'relative' }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '6px 16px', borderRadius: '999px', background: 'rgba(99,102,241,0.1)', border: '1px solid rgba(99,102,241,0.25)', color: '#a5b4fc', fontSize: '13px', fontWeight: 600, marginBottom: '1.5rem', letterSpacing: '0.01em' }}>
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#a5b4fc', display: 'inline-block' }} />
            Estamos contratando
          </div>
          <h1 style={{ fontSize: 'clamp(2.5rem, 5vw, 4rem)', fontWeight: 900, color: '#fff', lineHeight: 1.1, letterSpacing: '-0.03em', marginBottom: '1.5rem' }}>
            Faça parte do time<br />
            <span style={{ background: 'linear-gradient(135deg, #a5b4fc, #67e8f9)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', backgroundClip: 'text' }}>
              que constrói o futuro
            </span>
          </h1>
          <p style={{ fontSize: '1.15rem', color: 'rgba(255,255,255,0.5)', lineHeight: 1.7, maxWidth: '520px', margin: '0 auto' }}>
            Construímos tecnologia que impacta negócios reais. Se você quer trabalhar em um time focado, remoto e com propósito, você está no lugar certo.
          </p>
        </div>
      </section>

      {/* Perks */}
      <section style={{ padding: '4rem 2rem', maxWidth: '1200px', margin: '0 auto' }}>
        <h2 style={{ fontSize: '1.75rem', fontWeight: 800, color: '#fff', marginBottom: '2.5rem', letterSpacing: '-0.02em' }}>Por que a Meada?</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.25rem' }}>
          {perks.map((p) => (
            <div key={p.title} style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: '16px', padding: '1.75rem' }}>
              <div style={{ fontSize: '2rem', marginBottom: '0.75rem' }}>{p.emoji}</div>
              <div style={{ fontSize: '15px', fontWeight: 700, color: '#fff', marginBottom: '0.4rem' }}>{p.title}</div>
              <div style={{ fontSize: '14px', color: 'rgba(255,255,255,0.45)', lineHeight: 1.6 }}>{p.desc}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Vagas abertas */}
      <section style={{ padding: '4rem 2rem', maxWidth: '1200px', margin: '0 auto' }}>
        <h2 style={{ fontSize: '1.75rem', fontWeight: 800, color: '#fff', marginBottom: '0.5rem', letterSpacing: '-0.02em' }}>Vagas abertas</h2>
        <p style={{ color: 'rgba(255,255,255,0.4)', fontSize: '15px', marginBottom: '2rem' }}>{openings.length} posições disponíveis</p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {openings.map((o) => (
            <div key={o.role} style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: '16px', padding: '1.75rem 2rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '2rem', flexWrap: 'wrap', transition: 'border-color 0.2s', cursor: 'pointer' }}
              onMouseEnter={e => (e.currentTarget.style.borderColor = `${o.color}40`)}
              onMouseLeave={e => (e.currentTarget.style.borderColor = 'rgba(255,255,255,0.07)')}>
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
                  <span style={{ fontSize: '11px', fontWeight: 700, color: o.color, background: `${o.color}18`, border: `1px solid ${o.color}30`, padding: '3px 10px', borderRadius: '999px', letterSpacing: '0.04em', textTransform: 'uppercase' }}>{o.area}</span>
                  <span style={{ fontSize: '13px', color: 'rgba(255,255,255,0.35)' }}>{o.type}</span>
                </div>
                <div style={{ fontSize: '17px', fontWeight: 700, color: '#fff', marginBottom: '0.4rem' }}>{o.role}</div>
                <div style={{ fontSize: '14px', color: 'rgba(255,255,255,0.45)', lineHeight: 1.5 }}>{o.desc}</div>
              </div>
              <a href={`mailto:oi@meadadigital.com?subject=Candidatura: ${o.role}`} style={{ flexShrink: 0, padding: '10px 22px', background: `linear-gradient(135deg, ${o.color}, ${o.color}cc)`, border: 'none', borderRadius: '10px', color: '#fff', fontWeight: 600, fontSize: '14px', cursor: 'pointer', textDecoration: 'none', whiteSpace: 'nowrap' }}>
                Candidatar-se
              </a>
            </div>
          ))}
        </div>
      </section>

      {/* CTA candidatura espontânea */}
      <section style={{ padding: '5rem 2rem', maxWidth: '700px', margin: '0 auto', textAlign: 'center' }}>
        <div style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: '20px', padding: '3rem 2.5rem' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: '1rem' }}>📩</div>
          <h3 style={{ fontSize: '1.5rem', fontWeight: 800, color: '#fff', marginBottom: '0.75rem', letterSpacing: '-0.02em' }}>Não encontrou sua vaga?</h3>
          <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: '15px', lineHeight: 1.7, marginBottom: '1.75rem' }}>Manda seu currículo mesmo assim. Guardamos candidaturas para quando a vaga certa aparecer.</p>
          <a href="mailto:oi@meadadigital.com?subject=Candidatura Espontânea" style={{ display: 'inline-block', padding: '13px 32px', background: 'linear-gradient(135deg, #3b82f6, #6366f1)', borderRadius: '12px', color: '#fff', fontWeight: 600, fontSize: '15px', textDecoration: 'none' }}>
            Enviar currículo
          </a>
        </div>
      </section>

      <Footer />
    </div>
  );
}
