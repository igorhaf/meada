'use client';

import Navbar from '../components/Navbar';
import Footer from '../components/Footer';
import {
  HeartIcon,
  SparklesIcon,
  RocketIcon,
  TargetIcon,
} from '../components/icons';

const values = [
  {
    icon: HeartIcon,
    color: '#ec4899',
    gradientFrom: 'rgba(236,72,153,0.15)',
    gradientTo: 'rgba(236,72,153,0.05)',
    border: 'rgba(236,72,153,0.25)',
    title: 'Honestidade Acima de Tudo',
    description:
      'Só aceito projetos que consigo entregar bem. Prefiro dizer não do que entregar algo que não representa meu trabalho.',
  },
  {
    icon: SparklesIcon,
    color: '#3b82f6',
    gradientFrom: 'rgba(59,130,246,0.15)',
    gradientTo: 'rgba(59,130,246,0.05)',
    border: 'rgba(59,130,246,0.25)',
    title: 'Qualidade Sem Concessões',
    description:
      'Código limpo, interface bem pensada e atenção aos detalhes não são diferenciais — são o mínimo aceitável em cada entrega.',
  },
  {
    icon: RocketIcon,
    color: '#a855f7',
    gradientFrom: 'rgba(168,85,247,0.15)',
    gradientTo: 'rgba(168,85,247,0.05)',
    border: 'rgba(168,85,247,0.25)',
    title: 'Tecnologia Que Faz Sentido',
    description:
      'Não aplico IA por modismo. Analiso o projeto e integro tecnologia quando ela realmente agrega valor ao negócio do cliente.',
  },
  {
    icon: TargetIcon,
    color: '#10b981',
    gradientFrom: 'rgba(16,185,129,0.15)',
    gradientTo: 'rgba(16,185,129,0.05)',
    border: 'rgba(16,185,129,0.25)',
    title: 'Foco no Resultado',
    description:
      'Cada linha de código existe para atingir um objetivo real. Métricas de negócio, não só de desenvolvimento, guiam cada decisão.',
  },
];

const stats = [
  { value: '50+', label: 'Projetos entregues' },
  { value: '98%', label: 'Clientes satisfeitos' },
  { value: '5+', label: 'Anos de experiência' },
  { value: '20+', label: 'Tecnologias dominadas' },
];

const timeline = [
  { year: '2019', title: 'Primeiros projetos', description: 'Início da carreira desenvolvendo sites e sistemas para pequenas empresas, construindo experiência e portfólio.' },
  { year: '2021', title: 'Sistemas mais complexos', description: 'Expansão para projetos de maior porte: sistemas de gestão, APIs, painéis administrativos e integrações entre plataformas.' },
  { year: '2023', title: 'Integração com IA', description: 'Mergulho nos modelos de linguagem e automações com IA. Comecei a integrar essas tecnologias em projetos de clientes com resultados concretos.' },
  { year: '2024', title: 'Meada', description: 'Formalização da Meada como estúdio especializado em sites, sistemas e soluções com inteligência artificial aplicada.' },
];

export default function SobrePage() {
  return (
    <div
      style={{
        background: '#000812',
        minHeight: '100vh',
        color: '#fff',
        fontFamily: 'system-ui, -apple-system, sans-serif',
      }}
    >
      <Navbar />

      {/* Hero Section */}
      <section
        style={{
          paddingTop: '160px',
          paddingBottom: '100px',
          paddingLeft: '2rem',
          paddingRight: '2rem',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            position: 'absolute',
            top: '-120px',
            left: '-200px',
            width: '700px',
            height: '700px',
            background: 'radial-gradient(circle, rgba(139,92,246,0.1) 0%, transparent 70%)',
            pointerEvents: 'none',
          }}
        />
        <div
          style={{
            position: 'absolute',
            top: '-80px',
            right: '-100px',
            width: '500px',
            height: '500px',
            background: 'radial-gradient(circle, rgba(59,130,246,0.08) 0%, transparent 70%)',
            pointerEvents: 'none',
          }}
        />

        <div style={{ maxWidth: '1360px', margin: '0 auto', textAlign: 'center', position: 'relative' }}>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              padding: '6px 16px',
              borderRadius: '100px',
              background: 'rgba(139,92,246,0.1)',
              border: '1px solid rgba(139,92,246,0.25)',
              marginBottom: '1.5rem',
            }}
          >
            <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#8b5cf6' }} />
            <span style={{ color: '#c4b5fd', fontSize: '13px', fontWeight: 500, letterSpacing: '0.05em' }}>
              Sobre a Meada
            </span>
          </div>

          <h1
            style={{
              fontSize: 'clamp(2.5rem, 5vw, 4rem)',
              fontWeight: 800,
              lineHeight: 1.1,
              letterSpacing: '-0.03em',
              marginBottom: '1.5rem',
              color: '#fff',
            }}
          >
            Sites e Sistemas com{' '}
            <span
              style={{
                background: 'linear-gradient(135deg, #8b5cf6, #ec4899, #f97316)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
                backgroundClip: 'text',
              }}
            >
              Inteligência Real
            </span>
          </h1>

          <p
            style={{
              fontSize: '1.125rem',
              color: 'rgba(255,255,255,0.55)',
              maxWidth: '620px',
              margin: '0 auto',
              lineHeight: 1.7,
            }}
          >
            Sou desenvolvedor especializado em criar sites e sistemas sob medida, com ou sem integração de inteligência artificial — sempre com foco em qualidade, prazo e resultado real para o cliente.
          </p>
        </div>
      </section>

      {/* Story Section */}
      <section style={{ padding: '2rem 2rem 6rem' }}>
        <div
          style={{
            maxWidth: '1360px',
            margin: '0 auto',
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: '5rem',
            alignItems: 'start',
          }}
        >
          {/* Left: Story Text */}
          <div>
            <h2
              style={{
                fontSize: 'clamp(1.75rem, 3vw, 2.25rem)',
                fontWeight: 800,
                color: '#fff',
                letterSpacing: '-0.03em',
                marginBottom: '1.5rem',
              }}
            >
              Da web tradicional à IA aplicada
            </h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <p style={{ color: 'rgba(255,255,255,0.55)', fontSize: '15px', lineHeight: 1.8 }}>
                A Meada surgiu da minha trajetória como desenvolvedor — anos criando sites institucionais, lojas virtuais, sistemas de gestão e integrações para empresas de diferentes segmentos.
              </p>
              <p style={{ color: 'rgba(255,255,255,0.55)', fontSize: '15px', lineHeight: 1.8 }}>
                Com o avanço da inteligência artificial, passei a incorporar essas tecnologias nos projetos de forma prática: automações, chatbots, geração de conteúdo, análise de dados e muito mais. Não como tendência, mas como ferramenta real de resultado.
              </p>
              <p style={{ color: 'rgba(255,255,255,0.55)', fontSize: '15px', lineHeight: 1.8 }}>
                Hoje a Meada é especializada em dois mundos que se complementam: desenvolvimento web sólido e moderno, e integração inteligente de IA quando faz sentido para o negócio. Cada projeto recebe atenção total — do primeiro papo até o lançamento.
              </p>
            </div>
          </div>

          {/* Right: Timeline */}
          <div style={{ paddingTop: '0.5rem' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0' }}>
              {timeline.map((item, index) => (
                <div
                  key={item.year}
                  style={{
                    display: 'flex',
                    gap: '1.5rem',
                    paddingBottom: index < timeline.length - 1 ? '2rem' : '0',
                    position: 'relative',
                  }}
                >
                  {index < timeline.length - 1 && (
                    <div
                      style={{
                        position: 'absolute',
                        left: '19px',
                        top: '40px',
                        bottom: '0',
                        width: '1px',
                        background: 'rgba(59,130,246,0.2)',
                      }}
                    />
                  )}

                  <div style={{ flexShrink: 0, position: 'relative' }}>
                    <div
                      style={{
                        width: '40px',
                        height: '40px',
                        borderRadius: '50%',
                        background: 'rgba(59,130,246,0.1)',
                        border: '1px solid rgba(59,130,246,0.3)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      <div
                        style={{
                          width: '8px',
                          height: '8px',
                          borderRadius: '50%',
                          background: '#3b82f6',
                        }}
                      />
                    </div>
                  </div>

                  <div>
                    <div
                      style={{
                        display: 'inline-block',
                        padding: '2px 10px',
                        borderRadius: '6px',
                        background: 'rgba(59,130,246,0.1)',
                        border: '1px solid rgba(59,130,246,0.2)',
                        color: '#93c5fd',
                        fontSize: '12px',
                        fontWeight: 600,
                        marginBottom: '0.4rem',
                        fontFamily: 'monospace',
                      }}
                    >
                      {item.year}
                    </div>
                    <h4 style={{ color: '#fff', fontSize: '15px', fontWeight: 700, marginBottom: '0.35rem' }}>
                      {item.title}
                    </h4>
                    <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: '13px', lineHeight: 1.6 }}>
                      {item.description}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Values Section */}
      <section
        style={{
          padding: '5rem 2rem',
          background: 'rgba(255,255,255,0.01)',
          borderTop: '1px solid rgba(59,130,246,0.08)',
          borderBottom: '1px solid rgba(59,130,246,0.08)',
        }}
      >
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: '3.5rem' }}>
            <h2
              style={{
                fontSize: 'clamp(1.75rem, 3vw, 2.5rem)',
                fontWeight: 800,
                color: '#fff',
                letterSpacing: '-0.03em',
                marginBottom: '0.75rem',
              }}
            >
              Como Trabalho
            </h2>
            <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: '1rem' }}>
              Os princípios que guiam cada projeto.
            </p>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: '1.5rem',
            }}
          >
            {values.map((value) => {
              const Icon = value.icon;
              return (
                <div
                  key={value.title}
                  style={{
                    background: 'rgba(255,255,255,0.03)',
                    border: '1px solid rgba(255,255,255,0.07)',
                    borderRadius: '16px',
                    padding: '2rem',
                    display: 'flex',
                    gap: '1.25rem',
                    alignItems: 'flex-start',
                    transition: 'border-color 0.3s ease',
                    cursor: 'default',
                  }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLElement).style.borderColor = value.border;
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLElement).style.borderColor = 'rgba(255,255,255,0.07)';
                  }}
                >
                  <div
                    style={{
                      flexShrink: 0,
                      width: '48px',
                      height: '48px',
                      borderRadius: '14px',
                      background: `linear-gradient(135deg, ${value.gradientFrom}, ${value.gradientTo})`,
                      border: `1px solid ${value.border}`,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <Icon size={22} color={value.color} />
                  </div>
                  <div>
                    <h3
                      style={{
                        fontSize: '1rem',
                        fontWeight: 700,
                        color: '#fff',
                        marginBottom: '0.5rem',
                        letterSpacing: '-0.01em',
                      }}
                    >
                      {value.title}
                    </h3>
                    <p style={{ color: 'rgba(255,255,255,0.5)', fontSize: '14px', lineHeight: 1.7 }}>
                      {value.description}
                    </p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* Stats Section */}
      <section style={{ padding: '5rem 2rem' }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: '3rem' }}>
            <h2
              style={{
                fontSize: 'clamp(1.75rem, 3vw, 2.5rem)',
                fontWeight: 800,
                color: '#fff',
                letterSpacing: '-0.03em',
                marginBottom: '0.75rem',
              }}
            >
              Em Números
            </h2>
            <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: '1rem' }}>
              Resultados construídos projeto a projeto.
            </p>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(4, 1fr)',
              gap: '1.5rem',
            }}
          >
            {stats.map((stat) => (
              <div
                key={stat.label}
                style={{
                  background: 'rgba(255,255,255,0.03)',
                  border: '1px solid rgba(59,130,246,0.12)',
                  borderRadius: '16px',
                  padding: '2.5rem 2rem',
                  textAlign: 'center',
                }}
              >
                <div
                  style={{
                    fontSize: 'clamp(2rem, 3.5vw, 3rem)',
                    fontWeight: 800,
                    letterSpacing: '-0.03em',
                    background: 'linear-gradient(135deg, #fff, rgba(255,255,255,0.7))',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                    backgroundClip: 'text',
                    marginBottom: '0.5rem',
                  }}
                >
                  {stat.value}
                </div>
                <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: '14px' }}>{stat.label}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section style={{ padding: '5rem 2rem 7rem' }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <div
            style={{
              borderRadius: '24px',
              background: 'linear-gradient(135deg, rgba(139,92,246,0.12), rgba(59,130,246,0.08))',
              border: '1px solid rgba(139,92,246,0.2)',
              padding: '4rem',
              textAlign: 'center',
              position: 'relative',
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                position: 'absolute',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                width: '600px',
                height: '300px',
                background: 'radial-gradient(ellipse, rgba(139,92,246,0.08) 0%, transparent 70%)',
                pointerEvents: 'none',
              }}
            />
            <h2
              style={{
                fontSize: 'clamp(1.75rem, 3vw, 2.5rem)',
                fontWeight: 800,
                color: '#fff',
                letterSpacing: '-0.03em',
                marginBottom: '1rem',
                position: 'relative',
              }}
            >
              Tem um projeto em mente?
            </h2>
            <p
              style={{
                color: 'rgba(255,255,255,0.5)',
                fontSize: '1rem',
                maxWidth: '480px',
                margin: '0 auto 2.5rem',
                lineHeight: 1.7,
                position: 'relative',
              }}
            >
              Me conta o que você precisa. Site, sistema, integração com IA — vamos descobrir juntos a melhor solução.
            </p>
            <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', position: 'relative' }}>
              <a
                href="/contato"
                style={{
                  padding: '14px 32px',
                  borderRadius: '10px',
                  background: 'linear-gradient(135deg, #8b5cf6, #3b82f6)',
                  color: '#fff',
                  fontSize: '15px',
                  fontWeight: 600,
                  textDecoration: 'none',
                  transition: 'opacity 0.2s, transform 0.2s',
                  display: 'inline-block',
                }}
                onMouseEnter={(e) => {
                  (e.currentTarget as HTMLElement).style.opacity = '0.88';
                  (e.currentTarget as HTMLElement).style.transform = 'translateY(-2px)';
                }}
                onMouseLeave={(e) => {
                  (e.currentTarget as HTMLElement).style.opacity = '1';
                  (e.currentTarget as HTMLElement).style.transform = 'translateY(0)';
                }}
              >
                Falar sobre meu projeto
              </a>
              <a
                href="/produtos"
                style={{
                  padding: '14px 32px',
                  borderRadius: '10px',
                  background: 'transparent',
                  color: 'rgba(255,255,255,0.7)',
                  fontSize: '15px',
                  fontWeight: 600,
                  textDecoration: 'none',
                  border: '1px solid rgba(255,255,255,0.15)',
                  transition: 'border-color 0.2s, color 0.2s',
                  display: 'inline-block',
                }}
                onMouseEnter={(e) => {
                  (e.currentTarget as HTMLElement).style.borderColor = 'rgba(255,255,255,0.35)';
                  (e.currentTarget as HTMLElement).style.color = '#fff';
                }}
                onMouseLeave={(e) => {
                  (e.currentTarget as HTMLElement).style.borderColor = 'rgba(255,255,255,0.15)';
                  (e.currentTarget as HTMLElement).style.color = 'rgba(255,255,255,0.7)';
                }}
              >
                Ver produtos
              </a>
            </div>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
