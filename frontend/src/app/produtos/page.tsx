'use client';

import Navbar from '../components/Navbar';
import Footer from '../components/Footer';

const PRODUTOS = [
  {
    name: 'AmigoPet',
    category: 'Pet Care',
    desc: 'Plataforma completa para petshops e clínicas veterinárias: agendamentos, prontuários e gestão em um só lugar.',
    tags: ['Agendamentos', 'Prontuário Digital', 'Petshop'],
    accent: '#34d399',
    href: 'https://amigopet.meadadigital.com',
    thumb: '/images/amigopet.jpg',
  },
  {
    name: 'Bangalô',
    category: 'Hotelaria',
    desc: 'Sistema de reservas e gestão para pousadas e hotéis boutique, com painel de controle intuitivo.',
    tags: ['Reservas', 'Hotelaria', 'Painel Admin'],
    accent: '#f59e0b',
    href: 'https://bangalo.meadadigital.com',
    thumb: '/images/bangalo.jpg',
  },
  {
    name: 'Ateliê Rosendo',
    category: 'E-commerce',
    desc: 'Loja virtual artesanal com catálogo de produtos, carrinho e vitrine elegante para artesãos.',
    tags: ['Artesanato', 'Loja Virtual', 'Catálogo'],
    accent: '#f472b6',
    href: 'https://atelie-rosendo.meadadigital.com',
    thumb: '/images/atelie-rosendo.jpg',
  },
  {
    name: 'Aurora Motors',
    category: 'Automotivo',
    desc: 'Plataforma para concessionárias com vitrine de veículos, simulador de financiamento e agendamento.',
    tags: ['Veículos', 'Financiamento', 'Concessionária'],
    accent: '#60a5fa',
    href: 'https://aurora-motors.meadadigital.com',
    thumb: '/images/aurora-motors.jpg',
  },
  {
    name: 'Entre Linhas e Silêncios',
    category: 'Blog & Literatura',
    desc: 'Blog literário com gestão de conteúdo, categorias e experiência de leitura imersiva.',
    tags: ['Blog', 'Literatura', 'Conteúdo'],
    accent: '#a78bfa',
    href: 'https://entre-linhas-e-silencios.meadadigital.com',
    thumb: '/images/entre-linhas-e-silencios.jpg',
  },
  {
    name: 'Impacto Fitness',
    category: 'Fitness',
    desc: 'Site institucional para academia com planos, horários de aulas e área de membros.',
    tags: ['Academia', 'Membros', 'Planos'],
    accent: '#f97316',
    href: 'https://impacto-fitness.meadadigital.com',
    thumb: '/images/impacto-fitness.jpg',
  },
  {
    name: 'Kazen Sushi House',
    category: 'Gastronomia',
    desc: 'Cardápio digital e sistema de pedidos online para restaurante japonês com experiência premium.',
    tags: ['Restaurante', 'Cardápio Digital', 'Pedidos'],
    accent: '#fb7185',
    href: 'https://kazen-sushi-house.meadadigital.com',
    thumb: '/images/kazen-sushi-house.jpg',
  },
  {
    name: 'Leva e Lava',
    category: 'Serviços',
    desc: 'Plataforma de agendamento para lavanderias com coleta, entrega e acompanhamento em tempo real.',
    tags: ['Lavanderia', 'Agendamento', 'Delivery'],
    accent: '#22d3ee',
    href: 'https://levaelava.meadadigital.com',
    thumb: '/images/levaelava.jpg',
  },
  {
    name: 'Nobre Madeira',
    category: 'Marcenaria',
    desc: 'Site institucional para marcenaria de alto padrão com portfólio de móveis e orçamento online.',
    tags: ['Marcenaria', 'Portfólio', 'Móveis'],
    accent: '#d97706',
    href: 'https://nobre-madeira.meadadigital.com',
    thumb: '/images/nobre-madeira.jpg',
  },
  {
    name: 'Reservo',
    category: 'Reservas',
    desc: 'Sistema de reservas multi-segmento para barbearias, consultórios e espaços de eventos.',
    tags: ['Agendamento', 'Multi-segmento', 'Reservas'],
    accent: '#34d399',
    href: 'https://reservo.meadadigital.com',
    thumb: '/images/reservo.jpg',
  },
  {
    name: 'Suinda',
    category: 'Institucional',
    desc: 'Site institucional moderno com apresentação de serviços, equipe e formulário de contato.',
    tags: ['Institucional', 'Serviços', 'Contato'],
    accent: '#818cf8',
    href: 'https://suinda.meadadigital.com',
    thumb: '/images/suinda.jpg',
  },
  {
    name: 'Viva Pronto',
    category: 'Imobiliário',
    desc: 'Plataforma imobiliária com busca avançada de imóveis, filtros e tour virtual integrado.',
    tags: ['Imóveis', 'Busca Avançada', 'Tour Virtual'],
    accent: '#4ade80',
    href: 'https://vivapronto.meadadigital.com',
    thumb: '/images/vivapronto.jpg',
  },
];

const ALL_CATEGORIES = ['Todos', ...Array.from(new Set(PRODUTOS.map(p => p.category)))];

export default function ProdutosPage() {
  return (
    <div style={{ backgroundColor: '#000812', color: '#fff', minHeight: '100vh' }}>
      <Navbar />

      <section style={{ paddingTop: '148px', paddingBottom: '100px', padding: '148px 4rem 100px' }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>

          {/* Header */}
          <div style={{ marginBottom: '4rem' }}>
            <span style={{ fontSize: '11px', color: 'rgb(96,165,250)', textTransform: 'uppercase', letterSpacing: '0.1em', fontWeight: '700' }}>Produtos</span>
            <h1 style={{ fontSize: 'clamp(2.2rem, 4vw, 3.4rem)', fontWeight: '900', lineHeight: '1.15', marginTop: '1rem', marginBottom: '1rem' }}>
              Soluções Prontas para Usar
            </h1>
            <p style={{ fontSize: '17px', color: 'rgb(148,163,184)', lineHeight: '1.7', maxWidth: '560px' }}>
              Projetos desenvolvidos pela Meada Digital — cada um com propósito, identidade e tecnologia sob medida.
            </p>
          </div>

          {/* Grid */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1.5rem' }}>
            {PRODUTOS.map((p, i) => (
              <a
                key={i}
                href={p.href}
                target="_blank"
                rel="noopener noreferrer"
                style={{
                  borderRadius: '16px', overflow: 'hidden',
                  border: '1px solid rgba(59,130,246,0.1)',
                  background: 'rgba(15,23,42,0.4)',
                  transition: 'all 0.3s ease', cursor: 'pointer',
                  display: 'flex', flexDirection: 'column',
                  textDecoration: 'none', color: 'inherit',
                }}
                onMouseEnter={e => {
                  e.currentTarget.style.transform = 'translateY(-5px)';
                  e.currentTarget.style.border = `1px solid ${p.accent}40`;
                  e.currentTarget.style.boxShadow = `0 20px 40px ${p.accent}18`;
                  const img = e.currentTarget.querySelector('.thumb-img') as HTMLElement | null;
                  if (img) img.style.transform = 'scale(1.07)';
                }}
                onMouseLeave={e => {
                  e.currentTarget.style.transform = 'translateY(0)';
                  e.currentTarget.style.border = '1px solid rgba(59,130,246,0.1)';
                  e.currentTarget.style.boxShadow = 'none';
                  const img = e.currentTarget.querySelector('.thumb-img') as HTMLElement | null;
                  if (img) img.style.transform = 'scale(1)';
                }}
              >
                <div style={{ height: '160px', position: 'relative', overflow: 'hidden', background: '#0f172a' }}>
                  <img
                    className="thumb-img"
                    src={p.thumb}
                    alt={p.name}
                    style={{ width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'top', display: 'block', transition: 'transform 0.4s ease' }}
                  />
                  <div style={{ position: 'absolute', top: '0.75rem', left: '0.85rem', padding: '3px 9px', borderRadius: '24px', background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(8px)', border: `1px solid ${p.accent}40`, fontSize: '10px', fontWeight: '700', color: p.accent, letterSpacing: '0.07em', textTransform: 'uppercase' }}>{p.category}</div>
                </div>
                <div style={{ padding: '1.25rem', flex: 1, display: 'flex', flexDirection: 'column' }}>
                  <p style={{ fontSize: '16px', fontWeight: '800', color: '#e2e8f0', marginBottom: '0.4rem', letterSpacing: '-0.01em' }}>{p.name}</p>
                  <p style={{ fontSize: '12px', color: 'rgb(148,163,184)', lineHeight: '1.6', marginBottom: '1rem', flex: 1 }}>{p.desc}</p>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem', marginBottom: '1rem' }}>
                    {p.tags.map(t => (
                      <span key={t} style={{ padding: '2px 8px', borderRadius: '5px', background: 'rgba(59,130,246,0.08)', border: '1px solid rgba(59,130,246,0.15)', fontSize: '10px', fontWeight: '600', color: 'rgb(148,163,184)' }}>{t}</span>
                    ))}
                  </div>
                  <span style={{ color: p.accent, fontWeight: '600', fontSize: '13px' }}>Conhecer →</span>
                </div>
              </a>
            ))}
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
