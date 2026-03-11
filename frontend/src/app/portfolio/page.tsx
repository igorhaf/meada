'use client';

import { useState } from 'react';
import Link from 'next/link';
import Navbar from '../components/Navbar';
import Footer from '../components/Footer';

const CATEGORIES = ['Todos', 'Web', 'Mobile', 'Cloud', 'IA'];

const PROJECTS = [
  {
    id: 1,
    name: 'FinTrack',
    client: 'Fintech · Startup',
    category: 'Mobile',
    desc: 'Super-app financeiro com gestão de gastos, investimentos e cartões em tempo real. Mais de 200 mil usuários ativos no primeiro semestre.',
    tech: ['React Native', 'Node.js', 'PostgreSQL', 'Redis'],
    gradient: 'linear-gradient(135deg, #1d4ed8 0%, #4f46e5 100%)',
    accent: '#60a5fa',
    result: '+340% retenção',
  },
  {
    id: 2,
    name: 'NeuralHub',
    client: 'SaaS · Série B',
    category: 'IA',
    desc: 'Plataforma de análise preditiva com modelos de linguagem customizados para automação de processos industriais.',
    tech: ['Python', 'TensorFlow', 'FastAPI', 'React'],
    gradient: 'linear-gradient(135deg, #7c3aed 0%, #db2777 100%)',
    accent: '#a855f7',
    result: '80% menos tempo manual',
  },
  {
    id: 3,
    name: 'CloudOps Pro',
    client: 'Enterprise · IPO',
    category: 'Cloud',
    desc: 'Orquestração multi-cloud com automação de infra, monitoramento unificado e redução de custos operacionais.',
    tech: ['Kubernetes', 'Terraform', 'Go', 'AWS'],
    gradient: 'linear-gradient(135deg, #0891b2 0%, #1d4ed8 100%)',
    accent: '#22d3ee',
    result: '60% redução de custo',
  },
  {
    id: 4,
    name: 'EduFlow',
    client: 'EdTech · Escala nacional',
    category: 'Web',
    desc: 'LMS moderno com lives, gamificação e trilhas adaptativas por IA para mais de 50 mil alunos simultâneos.',
    tech: ['Next.js', 'TypeScript', 'Supabase', 'FFmpeg'],
    gradient: 'linear-gradient(135deg, #059669 0%, #0891b2 100%)',
    accent: '#34d399',
    result: '50k usuários simultâneos',
  },
  {
    id: 5,
    name: 'MedSync',
    client: 'Healthtech · B2B',
    category: 'Web',
    desc: 'Sistema de gestão hospitalar com prontuário eletrônico, telemedicina integrada e conformidade LGPD/CFM.',
    tech: ['React', 'Laravel', 'MySQL', 'Docker'],
    gradient: 'linear-gradient(135deg, #0369a1 0%, #059669 100%)',
    accent: '#38bdf8',
    result: '200+ hospitais parceiros',
  },
  {
    id: 6,
    name: 'RetailAI',
    client: 'Varejo · Multinacional',
    category: 'IA',
    desc: 'Motor de recomendação e previsão de demanda com visão computacional para gerenciamento de estoque autônomo.',
    tech: ['Python', 'Scikit-learn', 'Vue.js', 'Kafka'],
    gradient: 'linear-gradient(135deg, #d97706 0%, #dc2626 100%)',
    accent: '#fb923c',
    result: '+28% ticket médio',
  },
  {
    id: 7,
    name: 'LogiTrack',
    client: 'Logística · PME',
    category: 'Mobile',
    desc: 'App de rastreamento de entregas em tempo real com rota otimizada por IA e assinatura digital do recebedor.',
    tech: ['React Native', 'Express', 'MongoDB', 'MapBox'],
    gradient: 'linear-gradient(135deg, #b45309 0%, #15803d 100%)',
    accent: '#fbbf24',
    result: '35% menos falhas entrega',
  },
  {
    id: 8,
    name: 'SecureVault',
    client: 'Cibersegurança · B2B',
    category: 'Cloud',
    desc: 'Plataforma zero-trust com gestão de identidade, auditoria automática e conformidade SOC2/ISO27001.',
    tech: ['Go', 'Vault', 'Kubernetes', 'Terraform'],
    gradient: 'linear-gradient(135deg, #7c3aed 0%, #dc2626 100%)',
    accent: '#c084fc',
    result: 'Certificação SOC2 em 90 dias',
  },
];

function ProjectCard({ project }: { project: typeof PROJECTS[0] }) {
  const [hovered, setHovered] = useState(false);

  return (
    <div
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        borderRadius: '20px',
        overflow: 'hidden',
        border: `1px solid ${hovered ? project.accent + '50' : 'rgba(59,130,246,0.1)'}`,
        background: 'rgba(15,23,42,0.5)',
        transition: 'all 0.35s cubic-bezier(0.4,0,0.2,1)',
        transform: hovered ? 'translateY(-6px)' : 'translateY(0)',
        boxShadow: hovered ? `0 24px 48px ${project.accent}18` : 'none',
        cursor: 'pointer',
      }}
    >
      {/* Visual placeholder */}
      <div style={{
        height: '200px',
        background: project.gradient,
        position: 'relative',
        overflow: 'hidden',
      }}>
        {/* Decorative elements */}
        <div style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.25)' }} />
        <div style={{
          position: 'absolute', top: '-30%', right: '-10%',
          width: '200px', height: '200px', borderRadius: '50%',
          background: 'rgba(255,255,255,0.08)', filter: 'blur(30px)',
        }} />
        <div style={{
          position: 'absolute', bottom: '-20%', left: '-5%',
          width: '150px', height: '150px', borderRadius: '50%',
          background: 'rgba(255,255,255,0.05)', filter: 'blur(20px)',
        }} />
        {/* Category badge */}
        <div style={{
          position: 'absolute', top: '1rem', left: '1.25rem',
          padding: '5px 12px', borderRadius: '24px',
          background: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(8px)',
          border: `1px solid ${project.accent}40`,
          fontSize: '11px', fontWeight: '700', color: project.accent,
          letterSpacing: '0.07em', textTransform: 'uppercase',
        }}>{project.category}</div>
        {/* Result badge */}
        <div style={{
          position: 'absolute', bottom: '1rem', right: '1.25rem',
          padding: '5px 12px', borderRadius: '24px',
          background: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(8px)',
          fontSize: '12px', fontWeight: '700', color: '#fff',
        }}>{project.result}</div>
        {/* Project name overlay */}
        <div style={{
          position: 'absolute', bottom: '1rem', left: '1.25rem',
          fontSize: '22px', fontWeight: '900', color: '#fff',
          letterSpacing: '-0.02em',
        }}>{project.name}</div>
      </div>

      {/* Content */}
      <div style={{ padding: '1.75rem 1.75rem 1.5rem' }}>
        <p style={{ fontSize: '12px', color: project.accent, fontWeight: '600', marginBottom: '0.6rem', letterSpacing: '0.05em' }}>
          {project.client}
        </p>
        <p style={{ fontSize: '14px', color: 'rgb(148,163,184)', lineHeight: '1.7', marginBottom: '1.5rem' }}>
          {project.desc}
        </p>
        {/* Tech stack */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
          {project.tech.map(t => (
            <span key={t} style={{
              padding: '3px 10px', borderRadius: '6px',
              background: 'rgba(59,130,246,0.08)', border: '1px solid rgba(59,130,246,0.15)',
              fontSize: '11px', fontWeight: '600', color: 'rgb(148,163,184)',
            }}>{t}</span>
          ))}
        </div>
      </div>
    </div>
  );
}

export default function Portfolio() {
  const [active, setActive] = useState('Todos');

  const filtered = active === 'Todos' ? PROJECTS : PROJECTS.filter(p => p.category === active);

  return (
    <div style={{ backgroundColor: '#000812', color: '#fff', minHeight: '100vh' }}>
      <Navbar />

      {/* ── HERO ── */}
      <section style={{ position: 'relative', paddingTop: '160px', paddingBottom: '80px', overflow: 'hidden' }}>
        <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
          <div style={{ position: 'absolute', top: '-15%', left: '-8%', width: '600px', height: '600px', borderRadius: '50%', background: 'radial-gradient(circle, rgba(59,130,246,0.2) 0%, transparent 68%)', filter: 'blur(60px)' }} />
          <div style={{ position: 'absolute', top: '10%', right: '-12%', width: '700px', height: '700px', borderRadius: '50%', background: 'radial-gradient(circle, rgba(139,92,246,0.15) 0%, transparent 68%)', filter: 'blur(60px)' }} />
        </div>
        <div style={{ maxWidth: '1360px', margin: '0 auto', padding: '0 4rem', position: 'relative', zIndex: 1 }}>
          <span style={{
            display: 'inline-block', marginBottom: '2rem',
            padding: '7px 18px', borderRadius: '32px',
            background: 'rgba(59,130,246,0.1)', border: '1px solid rgba(59,130,246,0.28)',
            fontSize: '11px', fontWeight: '700', color: 'rgb(96,165,250)',
            letterSpacing: '0.07em', textTransform: 'uppercase',
          }}>Nosso Portfólio</span>

          <h1 style={{ fontSize: 'clamp(2.8rem, 5.5vw, 4.5rem)', fontWeight: '900', lineHeight: '1.1', letterSpacing: '-0.03em', marginBottom: '1.5rem', maxWidth: '800px' }}>
            Projetos que{' '}
            <span style={{ backgroundImage: 'linear-gradient(125deg, #60a5fa 0%, #a855f7 50%, #ec4899 100%)', backgroundClip: 'text', WebkitBackgroundClip: 'text', color: 'transparent' }}>
              Geram Resultados Reais
            </span>
          </h1>

          <p style={{ fontSize: '18px', color: 'rgb(203,213,225)', lineHeight: '1.8', maxWidth: '580px', marginBottom: '3rem' }}>
            Cada projeto é uma história de transformação. Veja como ajudamos empresas a crescerem com tecnologia de nível enterprise.
          </p>

          {/* Stats row */}
          <div style={{ display: 'flex', gap: '3.5rem' }}>
            {[
              { n: '500+', l: 'Projetos entregues' },
              { n: '98%', l: 'Satisfação dos clientes' },
              { n: '25', l: 'Países atendidos' },
            ].map(s => (
              <div key={s.l} style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <span style={{ fontSize: '1.8rem', fontWeight: '900', backgroundImage: 'linear-gradient(90deg, #60a5fa, #a855f7)', backgroundClip: 'text', WebkitBackgroundClip: 'text', color: 'transparent' }}>{s.n}</span>
                <span style={{ fontSize: '13px', color: 'rgb(148,163,184)', fontWeight: '500' }}>{s.l}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── FILTER + GRID ── */}
      <section style={{ padding: '3rem 4rem 9rem' }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>

          {/* Filter buttons */}
          <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '4rem', flexWrap: 'wrap' }}>
            {CATEGORIES.map(cat => (
              <button key={cat} onClick={() => setActive(cat)} style={{
                padding: '9px 22px', borderRadius: '32px', cursor: 'pointer',
                fontSize: '14px', fontWeight: '600',
                border: active === cat ? '1px solid rgba(59,130,246,0.6)' : '1px solid rgba(59,130,246,0.15)',
                background: active === cat ? 'rgba(59,130,246,0.15)' : 'transparent',
                color: active === cat ? 'rgb(96,165,250)' : 'rgb(148,163,184)',
                transition: 'all 0.2s ease',
              }}>
                {cat}
              </button>
            ))}
            <span style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', fontSize: '13px', color: 'rgb(100,116,139)' }}>
              {filtered.length} projeto{filtered.length !== 1 ? 's' : ''}
            </span>
          </div>

          {/* Grid */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '2rem' }}>
            {filtered.map(p => <ProjectCard key={p.id} project={p} />)}
          </div>
        </div>
      </section>

      {/* ── CTA ── */}
      <section style={{
        margin: '0 4rem 8rem',
        borderRadius: '24px',
        padding: '6rem 5rem',
        position: 'relative',
        overflow: 'hidden',
        background: 'linear-gradient(135deg, rgba(59,130,246,0.1) 0%, rgba(139,92,246,0.08) 100%)',
        border: '1px solid rgba(59,130,246,0.15)',
      }}>
        <div style={{ position: 'absolute', top: '-30%', right: '-5%', width: '500px', height: '500px', borderRadius: '50%', background: 'radial-gradient(circle, rgba(59,130,246,0.2) 0%, transparent 65%)', filter: 'blur(60px)' }} />
        <div style={{ maxWidth: '700px', position: 'relative', zIndex: 1 }}>
          <h2 style={{ fontSize: 'clamp(2rem, 4vw, 3rem)', fontWeight: '900', lineHeight: '1.2', marginBottom: '1.5rem' }}>
            Seu Projeto Pode Ser o Próximo Case de Sucesso
          </h2>
          <p style={{ fontSize: '17px', color: 'rgb(203,213,225)', lineHeight: '1.8', marginBottom: '2.5rem', maxWidth: '520px' }}>
            Conte-nos sobre sua ideia. Vamos analisar, planejar e executar com excelência do primeiro commit ao lançamento.
          </p>
          <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
            <Link href="/contato" style={{
              padding: '16px 36px', background: 'linear-gradient(135deg, #3b82f6, #6366f1)',
              border: 'none', borderRadius: '10px', color: 'white',
              fontWeight: '600', fontSize: '15px', textDecoration: 'none',
              boxShadow: '0 14px 32px rgba(59,130,246,0.35)',
              display: 'inline-block',
            }}>
              Iniciar Projeto
            </Link>
            <Link href="/servicos" style={{
              padding: '16px 36px', background: 'transparent',
              border: '1px solid rgba(59,130,246,0.28)', borderRadius: '10px',
              color: 'rgb(96,165,250)', fontWeight: '600', fontSize: '15px',
              textDecoration: 'none', display: 'inline-block',
            }}>
              Ver Serviços
            </Link>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
