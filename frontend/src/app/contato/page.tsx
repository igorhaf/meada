'use client';

import { useState } from 'react';
import Navbar from '../components/Navbar';
import Footer from '../components/Footer';
import { MailIcon, PhoneIcon, MapPinIcon } from '../components/icons';

interface FormState {
  nome: string;
  email: string;
  telefone: string;
  tipo: string;
  mensagem: string;
}

const faqItems = [
  {
    question: 'Quanto tempo leva para iniciar um projeto?',
    answer:
      'Após a assinatura do contrato e alinhamento inicial, geralmente iniciamos o desenvolvimento em até 5 dias úteis. Nossa equipe de discovery pode começar ainda mais rápido para projetos urgentes.',
  },
  {
    question: 'Vocês atendem empresas de que porte?',
    answer:
      'Atendemos desde startups em early stage até grandes corporações. Nossa estrutura é flexível e adaptamos a abordagem e o tamanho da equipe de acordo com a complexidade e o porte do projeto.',
  },
  {
    question: 'Como funciona o processo de desenvolvimento?',
    answer:
      'Seguimos metodologia ágil com sprints de 2 semanas. Cada sprint termina com uma demo do que foi desenvolvido, garantindo alinhamento constante e capacidade de ajuste rápido de prioridades.',
  },
  {
    question: 'Quais são as formas de pagamento?',
    answer:
      'Aceitamos transferência bancária, PIX e cartão de crédito. Para projetos grandes, trabalhamos com pagamento por milestone. Oferecemos contratos mensais para serviços contínuos de desenvolvimento.',
  },
];

export default function ContatoPage() {
  const [form, setForm] = useState<FormState>({
    nome: '',
    email: '',
    telefone: '',
    tipo: '',
    mensagem: '',
  });

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    console.log('Form data:', form);
    alert('Mensagem enviada com sucesso! Nossa equipe entrará em contato em breve.');
    setForm({ nome: '', email: '', telefone: '', tipo: '', mensagem: '' });
  };

  const inputStyle: React.CSSProperties = {
    width: '100%',
    padding: '14px 16px',
    borderRadius: '10px',
    background: 'rgba(15,23,42,0.6)',
    border: '1px solid rgba(59,130,246,0.2)',
    color: '#fff',
    fontSize: '14px',
    outline: 'none',
    transition: 'border-color 0.2s ease',
    boxSizing: 'border-box',
    fontFamily: 'system-ui, -apple-system, sans-serif',
  };

  const labelStyle: React.CSSProperties = {
    display: 'block',
    color: 'rgba(255,255,255,0.65)',
    fontSize: '13px',
    fontWeight: 500,
    marginBottom: '0.5rem',
    letterSpacing: '0.02em',
  };

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
          paddingBottom: '60px',
          paddingLeft: '2rem',
          paddingRight: '2rem',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        {/* Background Glows */}
        <div
          style={{
            position: 'absolute',
            top: '-100px',
            left: '-200px',
            width: '700px',
            height: '700px',
            background: 'radial-gradient(circle, rgba(59,130,246,0.1) 0%, transparent 70%)',
            pointerEvents: 'none',
          }}
        />
        <div
          style={{
            position: 'absolute',
            top: '-60px',
            right: '-100px',
            width: '500px',
            height: '500px',
            background: 'radial-gradient(circle, rgba(99,102,241,0.07) 0%, transparent 70%)',
            pointerEvents: 'none',
          }}
        />

        <div style={{ maxWidth: '1360px', margin: '0 auto', textAlign: 'center', position: 'relative' }}>
          {/* Badge */}
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              padding: '6px 16px',
              borderRadius: '100px',
              background: 'rgba(59,130,246,0.1)',
              border: '1px solid rgba(59,130,246,0.25)',
              marginBottom: '1.5rem',
            }}
          >
            <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#3b82f6' }} />
            <span style={{ color: '#93c5fd', fontSize: '13px', fontWeight: 500, letterSpacing: '0.05em' }}>
              Entre em Contato
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
            Vamos Construir Algo{' '}
            <span
              style={{
                background: 'linear-gradient(135deg, #3b82f6, #6366f1, #8b5cf6)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
                backgroundClip: 'text',
              }}
            >
              Incrível
            </span>
          </h1>

          <p
            style={{
              fontSize: '1.125rem',
              color: 'rgba(255,255,255,0.55)',
              maxWidth: '580px',
              margin: '0 auto',
              lineHeight: 1.7,
            }}
          >
            Tem um projeto em mente? Nossa equipe está pronta para transformar sua visão em realidade. Fale conosco.
          </p>
        </div>
      </section>

      {/* Main Content: Form + Info */}
      <section style={{ padding: '2rem 2rem 5rem' }}>
        <div
          style={{
            maxWidth: '1360px',
            margin: '0 auto',
            display: 'grid',
            gridTemplateColumns: '3fr 2fr',
            gap: '3rem',
            alignItems: 'start',
          }}
        >
          {/* LEFT: Form */}
          <div
            style={{
              background: 'rgba(255,255,255,0.03)',
              border: '1px solid rgba(59,130,246,0.12)',
              borderRadius: '20px',
              padding: '2.5rem',
            }}
          >
            <h2
              style={{
                fontSize: '1.4rem',
                fontWeight: 700,
                color: '#fff',
                letterSpacing: '-0.02em',
                marginBottom: '2rem',
              }}
            >
              Envie uma Mensagem
            </h2>

            <form onSubmit={handleSubmit}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
                {/* Nome */}
                <div>
                  <label style={labelStyle} htmlFor="nome">
                    Nome completo <span style={{ color: '#3b82f6' }}>*</span>
                  </label>
                  <input
                    id="nome"
                    name="nome"
                    type="text"
                    required
                    placeholder="Seu nome completo"
                    value={form.nome}
                    onChange={handleChange}
                    style={inputStyle}
                    onFocus={(e) => ((e.target as HTMLInputElement).style.borderColor = 'rgba(59,130,246,0.6)')}
                    onBlur={(e) => ((e.target as HTMLInputElement).style.borderColor = 'rgba(59,130,246,0.2)')}
                  />
                </div>

                {/* Email + Telefone Row */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                  <div>
                    <label style={labelStyle} htmlFor="email">
                      Email <span style={{ color: '#3b82f6' }}>*</span>
                    </label>
                    <input
                      id="email"
                      name="email"
                      type="email"
                      required
                      placeholder="seu@email.com"
                      value={form.email}
                      onChange={handleChange}
                      style={inputStyle}
                      onFocus={(e) => ((e.target as HTMLInputElement).style.borderColor = 'rgba(59,130,246,0.6)')}
                      onBlur={(e) => ((e.target as HTMLInputElement).style.borderColor = 'rgba(59,130,246,0.2)')}
                    />
                  </div>
                  <div>
                    <label style={labelStyle} htmlFor="telefone">
                      Telefone <span style={{ color: 'rgba(255,255,255,0.3)', fontSize: '11px' }}>(opcional)</span>
                    </label>
                    <input
                      id="telefone"
                      name="telefone"
                      type="tel"
                      placeholder="+55 (11) 9 9999-9999"
                      value={form.telefone}
                      onChange={handleChange}
                      style={inputStyle}
                      onFocus={(e) => ((e.target as HTMLInputElement).style.borderColor = 'rgba(59,130,246,0.6)')}
                      onBlur={(e) => ((e.target as HTMLInputElement).style.borderColor = 'rgba(59,130,246,0.2)')}
                    />
                  </div>
                </div>

                {/* Tipo do Projeto */}
                <div>
                  <label style={labelStyle} htmlFor="tipo">
                    Tipo do Projeto <span style={{ color: '#3b82f6' }}>*</span>
                  </label>
                  <select
                    id="tipo"
                    name="tipo"
                    required
                    value={form.tipo}
                    onChange={handleChange}
                    style={{
                      ...inputStyle,
                      cursor: 'pointer',
                      appearance: 'none',
                      WebkitAppearance: 'none',
                    }}
                    onFocus={(e) => ((e.target as HTMLSelectElement).style.borderColor = 'rgba(59,130,246,0.6)')}
                    onBlur={(e) => ((e.target as HTMLSelectElement).style.borderColor = 'rgba(59,130,246,0.2)')}
                  >
                    <option value="" disabled style={{ background: '#0f172a', color: 'rgba(255,255,255,0.4)' }}>
                      Selecione uma opção
                    </option>
                    <option value="web" style={{ background: '#0f172a' }}>Desenvolvimento Web</option>
                    <option value="mobile" style={{ background: '#0f172a' }}>Aplicativo Mobile</option>
                    <option value="cloud" style={{ background: '#0f172a' }}>Cloud & DevOps</option>
                    <option value="ia" style={{ background: '#0f172a' }}>Inteligência Artificial</option>
                    <option value="outro" style={{ background: '#0f172a' }}>Outro</option>
                  </select>
                </div>

                {/* Mensagem */}
                <div>
                  <label style={labelStyle} htmlFor="mensagem">
                    Mensagem <span style={{ color: '#3b82f6' }}>*</span>
                  </label>
                  <textarea
                    id="mensagem"
                    name="mensagem"
                    required
                    rows={5}
                    placeholder="Conte-nos sobre seu projeto, objetivos e desafios..."
                    value={form.mensagem}
                    onChange={handleChange}
                    style={{
                      ...inputStyle,
                      resize: 'vertical',
                      minHeight: '120px',
                      lineHeight: 1.6,
                    }}
                    onFocus={(e) => ((e.target as HTMLTextAreaElement).style.borderColor = 'rgba(59,130,246,0.6)')}
                    onBlur={(e) => ((e.target as HTMLTextAreaElement).style.borderColor = 'rgba(59,130,246,0.2)')}
                  />
                </div>

                {/* Submit */}
                <button
                  type="submit"
                  style={{
                    padding: '15px 32px',
                    borderRadius: '10px',
                    background: 'linear-gradient(135deg, #3b82f6, #6366f1)',
                    color: '#fff',
                    fontSize: '15px',
                    fontWeight: 600,
                    border: 'none',
                    cursor: 'pointer',
                    letterSpacing: '0.02em',
                    transition: 'opacity 0.2s, transform 0.2s',
                    alignSelf: 'flex-start',
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
                  Enviar Mensagem →
                </button>
              </div>
            </form>
          </div>

          {/* RIGHT: Contact Info */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
            <h2
              style={{
                fontSize: '1.4rem',
                fontWeight: 700,
                color: '#fff',
                letterSpacing: '-0.02em',
                marginBottom: '0.25rem',
              }}
            >
              Outras Formas de Contato
            </h2>

            {/* Info Cards */}
            <div
              style={{
                background: 'rgba(255,255,255,0.03)',
                border: '1px solid rgba(59,130,246,0.12)',
                borderRadius: '14px',
                padding: '1.25rem 1.5rem',
                display: 'flex',
                alignItems: 'center',
                gap: '1rem',
                transition: 'border-color 0.2s',
                cursor: 'default',
              }}
              onMouseEnter={(e) => ((e.currentTarget as HTMLElement).style.borderColor = 'rgba(59,130,246,0.3)')}
              onMouseLeave={(e) => ((e.currentTarget as HTMLElement).style.borderColor = 'rgba(59,130,246,0.12)')}
            >
              <div
                style={{
                  width: '42px',
                  height: '42px',
                  borderRadius: '10px',
                  background: 'rgba(59,130,246,0.1)',
                  border: '1px solid rgba(59,130,246,0.2)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                }}
              >
                <MailIcon size={18} color="#3b82f6" />
              </div>
              <div>
                <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: '12px', marginBottom: '2px', letterSpacing: '0.05em', textTransform: 'uppercase' }}>
                  Email
                </p>
                <a
                  href="mailto:contato@meada.dev"
                  style={{ color: '#fff', fontSize: '14px', fontWeight: 500, textDecoration: 'none' }}
                >
                  contato@meada.dev
                </a>
              </div>
            </div>

            <div
              style={{
                background: 'rgba(255,255,255,0.03)',
                border: '1px solid rgba(59,130,246,0.12)',
                borderRadius: '14px',
                padding: '1.25rem 1.5rem',
                display: 'flex',
                alignItems: 'center',
                gap: '1rem',
                transition: 'border-color 0.2s',
                cursor: 'default',
              }}
              onMouseEnter={(e) => ((e.currentTarget as HTMLElement).style.borderColor = 'rgba(59,130,246,0.3)')}
              onMouseLeave={(e) => ((e.currentTarget as HTMLElement).style.borderColor = 'rgba(59,130,246,0.12)')}
            >
              <div
                style={{
                  width: '42px',
                  height: '42px',
                  borderRadius: '10px',
                  background: 'rgba(16,185,129,0.1)',
                  border: '1px solid rgba(16,185,129,0.2)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                }}
              >
                <PhoneIcon size={18} color="#10b981" />
              </div>
              <div>
                <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: '12px', marginBottom: '2px', letterSpacing: '0.05em', textTransform: 'uppercase' }}>
                  WhatsApp
                </p>
                <a
                  href="https://wa.me/5511999999999"
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{ color: '#fff', fontSize: '14px', fontWeight: 500, textDecoration: 'none' }}
                >
                  +55 (11) 9 9999-9999
                </a>
              </div>
            </div>

            <div
              style={{
                background: 'rgba(255,255,255,0.03)',
                border: '1px solid rgba(59,130,246,0.12)',
                borderRadius: '14px',
                padding: '1.25rem 1.5rem',
                display: 'flex',
                alignItems: 'center',
                gap: '1rem',
                transition: 'border-color 0.2s',
                cursor: 'default',
              }}
              onMouseEnter={(e) => ((e.currentTarget as HTMLElement).style.borderColor = 'rgba(59,130,246,0.3)')}
              onMouseLeave={(e) => ((e.currentTarget as HTMLElement).style.borderColor = 'rgba(59,130,246,0.12)')}
            >
              <div
                style={{
                  width: '42px',
                  height: '42px',
                  borderRadius: '10px',
                  background: 'rgba(249,115,22,0.1)',
                  border: '1px solid rgba(249,115,22,0.2)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                }}
              >
                <MapPinIcon size={18} color="#f97316" />
              </div>
              <div>
                <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: '12px', marginBottom: '2px', letterSpacing: '0.05em', textTransform: 'uppercase' }}>
                  Sede
                </p>
                <p style={{ color: '#fff', fontSize: '14px', fontWeight: 500 }}>
                  São Paulo, SP – Brasil
                </p>
              </div>
            </div>

            {/* Social Links */}
            <div
              style={{
                background: 'rgba(255,255,255,0.03)',
                border: '1px solid rgba(255,255,255,0.07)',
                borderRadius: '14px',
                padding: '1.5rem',
              }}
            >
              <p
                style={{
                  color: 'rgba(255,255,255,0.45)',
                  fontSize: '12px',
                  letterSpacing: '0.05em',
                  textTransform: 'uppercase',
                  marginBottom: '1rem',
                }}
              >
                Redes Sociais
              </p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                {[
                  { label: 'LinkedIn', href: 'https://linkedin.com' },
                  { label: 'GitHub', href: 'https://github.com' },
                  { label: 'Twitter', href: 'https://twitter.com' },
                ].map((social) => (
                  <a
                    key={social.label}
                    href={social.href}
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{
                      color: 'rgba(255,255,255,0.5)',
                      fontSize: '14px',
                      textDecoration: 'none',
                      fontWeight: 500,
                      transition: 'color 0.2s',
                    }}
                    onMouseEnter={(e) => ((e.target as HTMLElement).style.color = '#93c5fd')}
                    onMouseLeave={(e) => ((e.target as HTMLElement).style.color = 'rgba(255,255,255,0.5)')}
                  >
                    {social.label} →
                  </a>
                ))}
              </div>
            </div>

            {/* Response Time Note */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '12px 16px',
                borderRadius: '10px',
                background: 'rgba(59,130,246,0.06)',
                border: '1px solid rgba(59,130,246,0.15)',
              }}
            >
              <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#22c55e', flexShrink: 0 }} />
              <p style={{ color: 'rgba(255,255,255,0.5)', fontSize: '13px' }}>
                Respondemos em até <span style={{ color: '#93c5fd', fontWeight: 600 }}>24 horas úteis</span>
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* FAQ Section */}
      <section
        style={{
          padding: '5rem 2rem 7rem',
          background: 'rgba(255,255,255,0.01)',
          borderTop: '1px solid rgba(59,130,246,0.08)',
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
              Perguntas Frequentes
            </h2>
            <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: '1rem' }}>
              Respondemos as dúvidas mais comuns sobre como trabalhamos.
            </p>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(2, 1fr)',
              gap: '1.25rem',
              maxWidth: '960px',
              margin: '0 auto',
            }}
          >
            {faqItems.map((item) => (
              <div
                key={item.question}
                style={{
                  background: 'rgba(255,255,255,0.03)',
                  border: '1px solid rgba(255,255,255,0.07)',
                  borderRadius: '14px',
                  padding: '1.75rem',
                  transition: 'border-color 0.2s',
                  cursor: 'default',
                }}
                onMouseEnter={(e) => ((e.currentTarget as HTMLElement).style.borderColor = 'rgba(59,130,246,0.2)')}
                onMouseLeave={(e) => ((e.currentTarget as HTMLElement).style.borderColor = 'rgba(255,255,255,0.07)')}
              >
                <p
                  style={{
                    color: '#93c5fd',
                    fontSize: '14px',
                    fontWeight: 600,
                    marginBottom: '0.75rem',
                    lineHeight: 1.5,
                  }}
                >
                  {item.question}
                </p>
                <p
                  style={{
                    color: 'rgba(255,255,255,0.45)',
                    fontSize: '13px',
                    lineHeight: 1.7,
                  }}
                >
                  {item.answer}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
