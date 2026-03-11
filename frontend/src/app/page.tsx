'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import {
  CodeIcon, CloudIcon, CpuIcon, SmartphoneIcon, LayersIcon, BarChartIcon,
  ZapIcon, LockIcon, SparklesIcon, RocketIcon, TargetIcon,
} from './components/icons';

function IconBox({ icon, color }: { icon: React.ReactNode; color: string }) {
  return (
    <div style={{
      width: '52px', height: '52px', borderRadius: '14px', marginBottom: '1.5rem',
      background: `linear-gradient(135deg, ${color}22, ${color}08)`,
      border: `1px solid ${color}30`,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      flexShrink: 0,
    }}>
      {icon}
    </div>
  );
}

export default function Home() {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  const services = [
    { Icon: CodeIcon, color: '#60a5fa', href: '/servicos/desenvolvimento', title: 'Desenvolvimento Personalizado', desc: 'Sites e sistemas feitos sob medida, do institucional ao mais complexo.' },
    { Icon: CloudIcon, color: '#a855f7', href: '/servicos/nuvem', title: 'Infraestrutura em Nuvem', desc: 'Deploy, CI/CD, monitoramento e escalabilidade sem dores de cabeça.' },
    { Icon: CpuIcon, color: '#ec4899', href: '/servicos/ia-automacao', title: 'IA & Automação', desc: 'Chatbots, automações e análise de dados aplicados ao seu negócio.' },
    { Icon: SmartphoneIcon, color: '#22d3ee', href: '/servicos/mobile', title: 'Design Mobile First', desc: 'Experiências nativas e fluidas em qualquer dispositivo e tamanho de tela.' },
    { Icon: LayersIcon, color: '#34d399', href: '/servicos/design-ux', title: 'Design & UX', desc: 'Interfaces bonitas e funcionais. Do wireframe ao Design System completo.' },
    { Icon: BarChartIcon, color: '#f97316', href: '/servicos/apis-integracoes', title: 'APIs & Integrações', desc: 'Pagamentos, CRMs, ERPs e qualquer sistema conectado em uma arquitetura coesa.' },
  ];

  const features = [
    { Icon: SparklesIcon, color: '#60a5fa', accent: 'rgba(96,165,250,0.1)', title: 'Atenção Obsessiva aos Detalhes', desc: 'Cada componente é construído com perfeição. Cada métrica monitorada. Entregamos produtos que superam qualquer expectativa.' },
    { Icon: RocketIcon, color: '#a855f7', accent: 'rgba(168,85,247,0.1)', title: 'Velocidade Sem Abrir Mão da Qualidade', desc: 'Entregue novas features toda semana sem comprometer a estabilidade. Nossos pipelines automatizados tornam rapidez e segurança inseparáveis.' },
    { Icon: TargetIcon, color: '#ec4899', accent: 'rgba(236,72,153,0.1)', title: 'Foco Total em Resultados', desc: 'Cada linha de código existe para atingir um objetivo de negócio claro. Definimos métricas desde o dia um e entregamos software que move os números que importam.' },
  ];

  return (
    <div style={{ backgroundColor: '#000812', color: '#fff', minHeight: '100vh' }}>
      <Navbar />

      {/* ── HERO ── */}
      <section style={{
        position: 'relative', minHeight: '100vh',
        paddingTop: '148px', paddingBottom: '100px', overflow: 'hidden',
      }}>
        <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
          <div style={{ position: 'absolute', top: '-15%', left: '-8%', width: '700px', height: '700px', borderRadius: '50%', background: 'radial-gradient(circle, rgba(59,130,246,0.22) 0%, transparent 68%)', filter: 'blur(60px)', animation: 'float 8s ease-in-out infinite' }} />
          <div style={{ position: 'absolute', top: '5%', right: '-12%', width: '800px', height: '800px', borderRadius: '50%', background: 'radial-gradient(circle, rgba(139,92,246,0.18) 0%, transparent 68%)', filter: 'blur(60px)', animation: 'float 11s ease-in-out infinite', animationDelay: '2s' }} />
          <div style={{ position: 'absolute', bottom: '-20%', left: '25%', width: '600px', height: '600px', borderRadius: '50%', background: 'radial-gradient(circle, rgba(236,72,153,0.12) 0%, transparent 68%)', filter: 'blur(60px)', animation: 'float 13s ease-in-out infinite', animationDelay: '4s' }} />
        </div>

        <div style={{ position: 'relative', zIndex: 1, maxWidth: '1360px', margin: '0 auto', padding: '0 4rem' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '7rem', alignItems: 'center' }}>

            {/* Left */}
            <div>
              <h1 style={{ fontSize: 'clamp(2.8rem, 5vw, 4.4rem)', fontWeight: '900', lineHeight: '1.12', letterSpacing: '-0.03em', marginBottom: '2rem' }}>
                Sites e Sistemas com o{' '}
                <span style={{ backgroundImage: 'linear-gradient(125deg, #60a5fa 0%, #a855f7 50%, #ec4899 100%)', backgroundClip: 'text', WebkitBackgroundClip: 'text', color: 'transparent' }}>
                  Diferencial da IA
                </span>
              </h1>

              <p style={{ fontSize: '18px', color: 'rgb(203,213,225)', lineHeight: '1.85', marginBottom: '2.5rem', maxWidth: '460px' }}>
                Desenvolvimento de sites e sistemas personalizados, com ou sem integração de inteligência artificial — sempre com qualidade e foco no que importa para o seu negócio.
              </p>

              <div style={{ display: 'flex', gap: '1rem', marginBottom: '4.5rem', flexWrap: 'wrap' }}>
                <button style={{ padding: '15px 34px', background: 'linear-gradient(135deg, #3b82f6, #6366f1)', border: 'none', borderRadius: '10px', color: 'white', fontWeight: '600', fontSize: '15px', cursor: 'pointer', boxShadow: '0 14px 32px rgba(59,130,246,0.38)', transition: 'all 0.3s ease' }}
                  onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-3px)'; e.currentTarget.style.boxShadow = '0 20px 44px rgba(59,130,246,0.48)'; }}
                  onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 14px 32px rgba(59,130,246,0.38)'; }}>
                  Comece Agora →
                </button>
                <button style={{ padding: '15px 34px', background: 'transparent', border: '1px solid rgba(59,130,246,0.28)', borderRadius: '10px', color: 'rgb(96,165,250)', fontWeight: '600', fontSize: '15px', cursor: 'pointer', transition: 'all 0.3s ease' }}
                  onMouseEnter={e => { e.currentTarget.style.background = 'rgba(59,130,246,0.08)'; e.currentTarget.style.borderColor = 'rgba(59,130,246,0.5)'; }}
                  onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.borderColor = 'rgba(59,130,246,0.28)'; }}>
                  Ver Portfólio
                </button>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '2rem', paddingTop: '3rem', borderTop: '1px solid rgba(71,85,105,0.25)' }}>
                {[
                  { n: '50+', l: 'Projetos', g: 'linear-gradient(90deg, #60a5fa, #a855f7)' },
                  { n: '20+', l: 'Tecnologias', g: 'linear-gradient(90deg, #a855f7, #ec4899)' },
                  { n: '5+', l: 'Anos no mercado', g: 'linear-gradient(90deg, #ec4899, #60a5fa)' },
                ].map(s => (
                  <div key={s.l}>
                    <p style={{ fontSize: '2.1rem', fontWeight: '900', marginBottom: '0.35rem', backgroundImage: s.g, backgroundClip: 'text', WebkitBackgroundClip: 'text', color: 'transparent' }}>{s.n}</p>
                    <p style={{ fontSize: '11px', color: 'rgb(148,163,184)', textTransform: 'uppercase', letterSpacing: '0.07em', fontWeight: '600' }}>{s.l}</p>
                  </div>
                ))}
              </div>
            </div>

            {/* Right - tech panel */}
            <div style={{ position: 'relative', height: '520px' }}>
              <div style={{ position: 'absolute', inset: 0, borderRadius: '24px', border: '1px solid rgba(59,130,246,0.12)', background: 'linear-gradient(135deg, rgba(59,130,246,0.06) 0%, rgba(139,92,246,0.03) 100%)', backdropFilter: 'blur(20px)' }}>
                {/* Card top-left */}
                <div style={{ position: 'absolute', top: '2.5rem', left: '2rem', width: '220px', padding: '1.5rem', borderRadius: '16px', border: '1px solid rgba(59,130,246,0.18)', background: 'rgba(15,23,42,0.85)', backdropFilter: 'blur(16px)' }}>
                  <div style={{ marginBottom: '0.75rem' }}><CodeIcon size={22} color="#60a5fa" /></div>
                  <p style={{ fontSize: '15px', fontWeight: '700', color: '#e2e8f0', marginBottom: '0.35rem' }}>Sites & Landing Pages</p>
                  <p style={{ fontSize: '12px', color: 'rgb(148,163,184)' }}>Presença digital que converte</p>
                </div>
                {/* Card center-right — IA em destaque */}
                <div style={{ position: 'absolute', top: '50%', right: '2rem', transform: 'translateY(-60%)', width: '220px', padding: '1.5rem', borderRadius: '16px', border: '1px solid rgba(139,92,246,0.3)', background: 'rgba(15,23,42,0.85)', backdropFilter: 'blur(16px)', boxShadow: '0 16px 40px rgba(139,92,246,0.15)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
                    <CpuIcon size={22} color="#a855f7" />
                    <span style={{ fontSize: '9px', fontWeight: '700', color: '#a855f7', textTransform: 'uppercase', letterSpacing: '0.08em', padding: '2px 8px', borderRadius: '20px', border: '1px solid rgba(139,92,246,0.35)', background: 'rgba(139,92,246,0.1)' }}>Diferencial</span>
                  </div>
                  <p style={{ fontSize: '15px', fontWeight: '700', color: '#e2e8f0', marginBottom: '0.35rem' }}>IA Integrada</p>
                  <p style={{ fontSize: '12px', color: 'rgb(148,163,184)' }}>Automações, chatbots e análise de dados</p>
                </div>
                {/* Card bottom-left */}
                <div style={{ position: 'absolute', bottom: '2.5rem', left: '2.5rem', width: '220px', padding: '1.5rem', borderRadius: '16px', border: '1px solid rgba(34,211,238,0.18)', background: 'rgba(15,23,42,0.85)', backdropFilter: 'blur(16px)' }}>
                  <div style={{ marginBottom: '0.75rem' }}><LayersIcon size={22} color="#22d3ee" /></div>
                  <p style={{ fontSize: '15px', fontWeight: '700', color: '#e2e8f0', marginBottom: '0.35rem' }}>Sistemas Web</p>
                  <p style={{ fontSize: '12px', color: 'rgb(148,163,184)' }}>Do painel ao sistema completo</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── SERVICES ── */}
      <section id="services" style={{
        padding: '9rem 4rem',
        borderTop: '1px solid rgba(59,130,246,0.1)',
        borderBottom: '1px solid rgba(59,130,246,0.1)',
        background: 'linear-gradient(180deg, transparent 0%, rgba(59,130,246,0.025) 50%, transparent 100%)',
      }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <div style={{ marginBottom: '5rem' }}>
            <span style={{ fontSize: '11px', color: 'rgb(96,165,250)', textTransform: 'uppercase', letterSpacing: '0.1em', fontWeight: '700' }}>Capacidades</span>
            <h2 style={{ fontSize: 'clamp(2.2rem, 4vw, 3.2rem)', fontWeight: '900', lineHeight: '1.2', marginTop: '1rem' }}>Tudo o Que Você Precisa para Crescer</h2>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '2rem' }}>
            {services.map((s, i) => (
              <div key={i} style={{ padding: '2.5rem', borderRadius: '16px', border: '1px solid rgba(59,130,246,0.1)', background: 'rgba(15,23,42,0.4)', backdropFilter: 'blur(12px)', cursor: 'pointer', transition: 'all 0.3s ease', display: 'flex', flexDirection: 'column' }}
                onMouseEnter={e => { e.currentTarget.style.border = `1px solid ${s.color}40`; e.currentTarget.style.transform = 'translateY(-6px)'; e.currentTarget.style.boxShadow = `0 20px 44px ${s.color}15`; e.currentTarget.style.background = 'rgba(15,23,42,0.7)'; }}
                onMouseLeave={e => { e.currentTarget.style.border = '1px solid rgba(59,130,246,0.1)'; e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = 'none'; e.currentTarget.style.background = 'rgba(15,23,42,0.4)'; }}>
                <IconBox icon={<s.Icon size={24} color={s.color} />} color={s.color} />
                <h3 style={{ fontSize: '18px', fontWeight: '700', marginBottom: '0.75rem', lineHeight: '1.3' }}>{s.title}</h3>
                <p style={{ color: 'rgb(148,163,184)', fontSize: '14px', lineHeight: '1.7', marginBottom: '1.75rem', flex: 1 }}>{s.desc}</p>
                <Link href={s.href} style={{ color: s.color, fontWeight: '600', fontSize: '14px', textDecoration: 'none' }}>Saiba mais →</Link>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── FEATURES ── */}
      <section id="features" style={{ padding: '9rem 4rem' }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <div style={{ marginBottom: '7rem' }}>
            <span style={{ fontSize: '11px', color: 'rgb(96,165,250)', textTransform: 'uppercase', letterSpacing: '0.1em', fontWeight: '700' }}>Por Que Meada</span>
            <h2 style={{ fontSize: 'clamp(2.2rem, 4vw, 3.2rem)', fontWeight: '900', lineHeight: '1.2', marginTop: '1rem' }}>Por Que Escolher a Meada</h2>
          </div>

          {features.map((f, i) => (
            <div key={i} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '7rem', alignItems: 'center', marginBottom: i < 2 ? '9rem' : '0' }}>
              <div style={{ order: i % 2 === 1 ? 2 : 1 }}>
                <div style={{ marginBottom: '2rem' }}>
                  <IconBox icon={<f.Icon size={26} color={f.color} />} color={f.color} />
                </div>
                <h3 style={{ fontSize: 'clamp(1.7rem, 3vw, 2.5rem)', fontWeight: '900', lineHeight: '1.2', marginBottom: '1.5rem' }}>{f.title}</h3>
                <p style={{ fontSize: '17px', color: 'rgb(203,213,225)', lineHeight: '1.85', marginBottom: '2.5rem', maxWidth: '500px' }}>{f.desc}</p>
                <button style={{ padding: '12px 28px', background: 'transparent', border: '1px solid rgba(59,130,246,0.28)', borderRadius: '8px', color: 'rgb(96,165,250)', fontWeight: '600', fontSize: '14px', cursor: 'pointer', transition: 'all 0.3s ease' }}
                  onMouseEnter={e => { e.currentTarget.style.background = 'rgba(59,130,246,0.08)'; }}
                  onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; }}>
                  Saiba Mais →
                </button>
              </div>
              <div style={{ height: '420px', order: i % 2 === 1 ? 1 : 2, borderRadius: '20px', border: '1px solid rgba(59,130,246,0.14)', background: `linear-gradient(135deg, ${f.accent} 0%, rgba(15,23,42,0.4) 100%)`, backdropFilter: 'blur(20px)' }} />
            </div>
          ))}
        </div>
      </section>

      {/* ── PORTFOLIO HIGHLIGHT ── */}
      <section style={{
        padding: '9rem 4rem',
        borderTop: '1px solid rgba(59,130,246,0.1)',
      }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '4rem', flexWrap: 'wrap', gap: '2rem' }}>
            <div>
              <span style={{ fontSize: '11px', color: 'rgb(96,165,250)', textTransform: 'uppercase', letterSpacing: '0.1em', fontWeight: '700' }}>Portfólio</span>
              <h2 style={{ fontSize: 'clamp(2.2rem, 4vw, 3.2rem)', fontWeight: '900', lineHeight: '1.2', marginTop: '1rem' }}>
                Projetos que Falam por Si
              </h2>
            </div>
            <a href="/portfolio" style={{
              padding: '12px 28px', borderRadius: '10px',
              border: '1px solid rgba(59,130,246,0.28)', background: 'transparent',
              color: 'rgb(96,165,250)', fontWeight: '600', fontSize: '14px',
              textDecoration: 'none', whiteSpace: 'nowrap',
              transition: 'all 0.2s ease',
            }}
            onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = 'rgba(59,130,246,0.08)'; }}
            onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = 'transparent'; }}>
              Ver portfólio completo →
            </a>
          </div>

          {/* Featured projects - 3 cards */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '2rem' }}>
            {[
              {
                name: 'FinTrack',
                client: 'Fintech · Startup',
                category: 'Mobile',
                desc: 'Super-app financeiro com gestão de gastos e investimentos. 200k usuários no primeiro semestre.',
                tech: ['React Native', 'Node.js', 'Redis'],
                gradient: 'linear-gradient(135deg, #1d4ed8 0%, #4f46e5 100%)',
                accent: '#60a5fa',
                result: '+340% retenção',
              },
              {
                name: 'NeuralHub',
                client: 'SaaS · Série B',
                category: 'IA',
                desc: 'Plataforma de análise preditiva com LLMs customizados para automação industrial.',
                tech: ['Python', 'TensorFlow', 'FastAPI'],
                gradient: 'linear-gradient(135deg, #7c3aed 0%, #db2777 100%)',
                accent: '#a855f7',
                result: '80% menos trabalho manual',
              },
              {
                name: 'CloudOps Pro',
                client: 'Enterprise · IPO',
                category: 'Cloud',
                desc: 'Orquestração multi-cloud com monitoramento unificado e 60% de redução nos custos de infra.',
                tech: ['Kubernetes', 'Terraform', 'Go'],
                gradient: 'linear-gradient(135deg, #0891b2 0%, #1d4ed8 100%)',
                accent: '#22d3ee',
                result: '60% redução de custo',
              },
            ].map((p, i) => (
              <div key={i} style={{
                borderRadius: '20px', overflow: 'hidden',
                border: '1px solid rgba(59,130,246,0.1)',
                background: 'rgba(15,23,42,0.4)',
                transition: 'all 0.3s ease', cursor: 'pointer',
              }}
              onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-6px)'; e.currentTarget.style.border = `1px solid ${p.accent}40`; e.currentTarget.style.boxShadow = `0 24px 48px ${p.accent}18`; }}
              onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.border = '1px solid rgba(59,130,246,0.1)'; e.currentTarget.style.boxShadow = 'none'; }}>
                {/* Visual */}
                <div style={{ height: '190px', background: p.gradient, position: 'relative', overflow: 'hidden' }}>
                  <div style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.22)' }} />
                  <div style={{ position: 'absolute', top: '-20%', right: '-8%', width: '180px', height: '180px', borderRadius: '50%', background: 'rgba(255,255,255,0.07)', filter: 'blur(24px)' }} />
                  <div style={{ position: 'absolute', top: '1rem', left: '1.25rem', padding: '4px 11px', borderRadius: '24px', background: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(8px)', border: `1px solid ${p.accent}40`, fontSize: '11px', fontWeight: '700', color: p.accent, letterSpacing: '0.07em', textTransform: 'uppercase' }}>{p.category}</div>
                  <div style={{ position: 'absolute', bottom: '1rem', right: '1.25rem', padding: '4px 11px', borderRadius: '24px', background: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(8px)', fontSize: '11px', fontWeight: '700', color: '#fff' }}>{p.result}</div>
                  <div style={{ position: 'absolute', bottom: '1rem', left: '1.25rem', fontSize: '21px', fontWeight: '900', color: '#fff', letterSpacing: '-0.02em' }}>{p.name}</div>
                </div>
                {/* Body */}
                <div style={{ padding: '1.5rem' }}>
                  <p style={{ fontSize: '12px', color: p.accent, fontWeight: '600', marginBottom: '0.5rem', letterSpacing: '0.04em' }}>{p.client}</p>
                  <p style={{ fontSize: '13px', color: 'rgb(148,163,184)', lineHeight: '1.65', marginBottom: '1.25rem' }}>{p.desc}</p>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem' }}>
                    {p.tech.map(t => (
                      <span key={t} style={{ padding: '3px 9px', borderRadius: '5px', background: 'rgba(59,130,246,0.08)', border: '1px solid rgba(59,130,246,0.15)', fontSize: '11px', fontWeight: '600', color: 'rgb(148,163,184)' }}>{t}</span>
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── CTA ── */}
      <section id="contact" style={{ padding: '11rem 4rem', position: 'relative', overflow: 'hidden' }}>
        <div style={{ position: 'absolute', top: '-25%', right: '-5%', width: '600px', height: '600px', background: 'radial-gradient(circle, rgba(59,130,246,0.22) 0%, transparent 65%)', borderRadius: '50%', filter: 'blur(70px)' }} />
        <div style={{ position: 'absolute', bottom: '-25%', left: '5%', width: '600px', height: '600px', background: 'radial-gradient(circle, rgba(139,92,246,0.18) 0%, transparent 65%)', borderRadius: '50%', filter: 'blur(70px)' }} />
        <div style={{ maxWidth: '780px', margin: '0 auto', textAlign: 'center', position: 'relative', zIndex: 1 }}>
          <h2 style={{ fontSize: 'clamp(2.2rem, 5vw, 3.8rem)', fontWeight: '900', lineHeight: '1.15', marginBottom: '2rem' }}>
            Pronto para{' '}
            <span style={{ backgroundImage: 'linear-gradient(125deg, #60a5fa, #a855f7, #ec4899)', backgroundClip: 'text', WebkitBackgroundClip: 'text', color: 'transparent' }}>
              Transformar seu Negócio?
            </span>
          </h2>
          <p style={{ fontSize: '18px', color: 'rgb(203,213,225)', lineHeight: '1.85', marginBottom: '3.5rem', maxWidth: '560px', margin: '0 auto 3.5rem' }}>
            Do site institucional ao sistema completo. Com integração de IA quando faz sentido. Sem enrolação, com resultado.
          </p>
          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
            <button style={{ padding: '13px 32px', background: 'linear-gradient(135deg, #3b82f6, #6366f1)', border: 'none', borderRadius: '12px', color: 'white', fontWeight: '600', fontSize: '15px', cursor: 'pointer', boxShadow: '0 8px 24px rgba(59,130,246,0.3)', transition: 'all 0.22s ease', letterSpacing: '0.01em' }}
              onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.boxShadow = '0 16px 36px rgba(59,130,246,0.45)'; }}
              onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 8px 24px rgba(59,130,246,0.3)'; }}>
              Agendar Consultoria
            </button>
            <button style={{ padding: '13px 32px', background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.12)', borderRadius: '12px', color: 'rgba(255,255,255,0.75)', fontWeight: '600', fontSize: '15px', cursor: 'pointer', transition: 'all 0.22s ease', letterSpacing: '0.01em' }}
              onMouseEnter={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.08)'; e.currentTarget.style.borderColor = 'rgba(99,102,241,0.5)'; e.currentTarget.style.color = 'white'; }}
              onMouseLeave={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.04)'; e.currentTarget.style.borderColor = 'rgba(255,255,255,0.12)'; e.currentTarget.style.color = 'rgba(255,255,255,0.75)'; }}>
              Ver Portfólio
            </button>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
