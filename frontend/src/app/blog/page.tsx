'use client';

import Navbar from '../components/Navbar';
import Footer from '../components/Footer';

const featured = {
  category: 'IA & Automação',
  categoryColor: '#a5b4fc',
  date: '5 de março de 2026',
  title: 'Como a IA pode responder seus clientes no WhatsApp enquanto você dorme',
  excerpt: 'Restaurantes, salões e lojas estão usando assistentes de IA para nunca perder um cliente por falta de resposta. Descubra como funciona e quanto custa para o seu negócio.',
  author: 'Igor Hafner',
  initials: 'IH',
  readTime: '8 min',
  gradient: 'linear-gradient(135deg, #6366f1, #06b6d4)',
  emoji: '🤖',
};

const posts = [
  { category: 'Marketing Digital', categoryColor: '#f472b6', date: '28 fev 2026', title: '5 formas de usar o WhatsApp para vender mais no seu negócio local', excerpt: 'O WhatsApp virou vitrine, catálogo e caixa registradora. Veja como pequenos negócios estão faturando mais.', author: 'Ana Paula', initials: 'AP', readTime: '6 min', gradient: 'linear-gradient(135deg,#db2777,#9333ea)', emoji: '📱' },
  { category: 'Sites', categoryColor: '#60a5fa', date: '20 fev 2026', title: 'Por que seu negócio perde clientes sem um site no Google', excerpt: '92% das pessoas pesquisam no Google antes de visitar um estabelecimento. Sem site, você não existe para elas.', author: 'Lucas Moreira', initials: 'LM', readTime: '5 min', gradient: 'linear-gradient(135deg,#0891b2,#06b6d4)', emoji: '🌐' },
  { category: 'Gestão', categoryColor: '#34d399', date: '12 fev 2026', title: 'Chega de planilha! Como um sistema simples pode organizar seu negócio', excerpt: 'Controlar estoque e clientes no Excel gera erros e perde tempo. Descubra como um sistema muda o dia a dia.', author: 'Fernanda Rocha', initials: 'FR', readTime: '7 min', gradient: 'linear-gradient(135deg,#059669,#0d9488)', emoji: '📊' },
  { category: 'E-commerce', categoryColor: '#fb923c', date: '5 fev 2026', title: 'Como abri uma loja virtual e comecei a vender em 7 dias', excerpt: 'Uma lojista de Curitiba conta como saiu do zero para faturar online em uma semana com ajuda da Meada.', author: 'Igor Hafner', initials: 'IH', readTime: '10 min', gradient: 'linear-gradient(135deg,#ea580c,#dc2626)', emoji: '🛒' },
  { category: 'IA', categoryColor: '#a5b4fc', date: '28 jan 2026', title: 'IA no seu negócio: 3 formas práticas para começar hoje', excerpt: 'Inteligência artificial não é só para grandes empresas. Veja três formas simples de colocar IA para trabalhar.', author: 'Fernanda Rocha', initials: 'FR', readTime: '9 min', gradient: 'linear-gradient(135deg,#7c3aed,#4f46e5)', emoji: '🤖' },
  { category: 'Reputação', categoryColor: '#fbbf24', date: '20 jan 2026', title: 'Como conseguir mais avaliações 5 estrelas no Google Maps', excerpt: 'Avaliações positivas no Google são o melhor marketing para negócios locais. Aprenda estratégias que funcionam.', author: 'Ana Paula', initials: 'AP', readTime: '5 min', gradient: 'linear-gradient(135deg,#ca8a04,#d97706)', emoji: '⭐' },
];

export default function BlogPage() {
  return (
    <div style={{ background: '#000812', minHeight: '100vh' }}>
      <Navbar />

      {/* Hero */}
      <section style={{ paddingTop: '8rem', paddingBottom: '4rem', textAlign: 'center', position: 'relative', overflow: 'hidden' }}>
        <div style={{ position: 'absolute', top: '20%', left: '50%', transform: 'translateX(-50%)', width: '700px', height: '300px', background: 'radial-gradient(ellipse, rgba(99,102,241,0.1) 0%, transparent 70%)', pointerEvents: 'none' }} />
        <div style={{ maxWidth: '700px', margin: '0 auto', padding: '0 2rem', position: 'relative' }}>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '6px 16px', borderRadius: '999px', background: 'rgba(99,102,241,0.1)', border: '1px solid rgba(99,102,241,0.25)', color: '#a5b4fc', fontSize: '13px', fontWeight: 600, marginBottom: '1.5rem' }}>
            Blog
          </div>
          <h1 style={{ fontSize: 'clamp(2.5rem, 5vw, 4rem)', fontWeight: 900, color: '#fff', lineHeight: 1.1, letterSpacing: '-0.03em', marginBottom: '1.25rem' }}>
            Dicas e novidades<br />
            <span style={{ background: 'linear-gradient(135deg, #a5b4fc, #67e8f9)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', backgroundClip: 'text' }}>
              para o seu negócio
            </span>
          </h1>
          <p style={{ fontSize: '1.1rem', color: 'rgba(255,255,255,0.5)', lineHeight: 1.7 }}>
            Conteúdo prático sobre tecnologia, marketing digital e IA para quem empreende.
          </p>
        </div>
      </section>

      <div style={{ maxWidth: '1200px', margin: '0 auto', padding: '0 2rem 6rem' }}>
        {/* Featured */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '2rem', marginBottom: '3rem', alignItems: 'center', background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: '20px', overflow: 'hidden' }}>
          <div style={{ height: '320px', display: 'flex', alignItems: 'center', justifyContent: 'center', background: featured.gradient, position: 'relative', overflow: 'hidden' }}>
            <div style={{ position: 'absolute', inset: 0, backgroundImage: 'radial-gradient(circle, rgba(139,92,246,0.18) 1px, transparent 1px)', backgroundSize: '28px 28px', opacity: 0.3 }} />
            <span style={{ fontSize: '5rem', position: 'relative' }}>{featured.emoji}</span>
          </div>
          <div style={{ padding: '2.5rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem' }}>
              <span style={{ fontSize: '11px', fontWeight: 700, color: featured.categoryColor, background: `${featured.categoryColor}18`, border: `1px solid ${featured.categoryColor}30`, padding: '3px 10px', borderRadius: '999px', letterSpacing: '0.04em' }}>{featured.category}</span>
              <span style={{ fontSize: '13px', color: 'rgba(255,255,255,0.35)' }}>{featured.date}</span>
            </div>
            <h2 style={{ fontSize: '1.5rem', fontWeight: 800, color: '#fff', lineHeight: 1.3, letterSpacing: '-0.02em', marginBottom: '1rem' }}>{featured.title}</h2>
            <p style={{ fontSize: '14px', color: 'rgba(255,255,255,0.5)', lineHeight: 1.7, marginBottom: '1.5rem' }}>{featured.excerpt}</p>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <div style={{ width: '32px', height: '32px', borderRadius: '50%', background: 'linear-gradient(135deg,#6366f1,#06b6d4)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '12px', fontWeight: 700, color: '#fff' }}>{featured.initials}</div>
              <div>
                <div style={{ fontSize: '13px', fontWeight: 600, color: '#fff' }}>{featured.author}</div>
                <div style={{ fontSize: '12px', color: 'rgba(255,255,255,0.35)' }}>{featured.readTime} de leitura</div>
              </div>
            </div>
          </div>
        </div>

        {/* Grid */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: '1.25rem' }}>
          {posts.map((post) => (
            <article key={post.title} style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: '16px', overflow: 'hidden', transition: 'border-color 0.2s, transform 0.2s', cursor: 'pointer' }}
              onMouseEnter={e => { e.currentTarget.style.borderColor = 'rgba(99,102,241,0.3)'; e.currentTarget.style.transform = 'translateY(-3px)'; }}
              onMouseLeave={e => { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.07)'; e.currentTarget.style.transform = 'translateY(0)'; }}>
              <div style={{ height: '140px', display: 'flex', alignItems: 'center', justifyContent: 'center', background: post.gradient, fontSize: '3rem', position: 'relative', overflow: 'hidden' }}>
                <div style={{ position: 'absolute', inset: 0, backgroundImage: 'radial-gradient(circle, rgba(255,255,255,0.12) 1px, transparent 1px)', backgroundSize: '24px 24px' }} />
                <span style={{ position: 'relative' }}>{post.emoji}</span>
              </div>
              <div style={{ padding: '1.5rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.75rem' }}>
                  <span style={{ fontSize: '11px', fontWeight: 700, color: post.categoryColor, background: `${post.categoryColor}15`, border: `1px solid ${post.categoryColor}25`, padding: '2px 9px', borderRadius: '999px' }}>{post.category}</span>
                  <span style={{ fontSize: '12px', color: 'rgba(255,255,255,0.3)' }}>{post.date}</span>
                </div>
                <h3 style={{ fontSize: '15px', fontWeight: 700, color: '#fff', lineHeight: 1.4, letterSpacing: '-0.01em', marginBottom: '0.6rem' }}>{post.title}</h3>
                <p style={{ fontSize: '13px', color: 'rgba(255,255,255,0.45)', lineHeight: 1.6, marginBottom: '1.25rem' }}>{post.excerpt}</p>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                  <div style={{ width: '28px', height: '28px', borderRadius: '50%', background: 'linear-gradient(135deg,#6366f1,#06b6d4)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '10px', fontWeight: 700, color: '#fff' }}>{post.initials}</div>
                  <div>
                    <div style={{ fontSize: '12px', fontWeight: 600, color: 'rgba(255,255,255,0.7)' }}>{post.author}</div>
                    <div style={{ fontSize: '11px', color: 'rgba(255,255,255,0.3)' }}>{post.readTime} de leitura</div>
                  </div>
                </div>
              </div>
            </article>
          ))}
        </div>

        {/* Newsletter */}
        <div style={{ marginTop: '4rem', background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: '20px', padding: '3rem', textAlign: 'center' }}>
          <div style={{ fontSize: '2rem', marginBottom: '1rem' }}>📬</div>
          <h3 style={{ fontSize: '1.5rem', fontWeight: 800, color: '#fff', marginBottom: '0.5rem', letterSpacing: '-0.02em' }}>Receba dicas toda semana</h3>
          <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: '14px', marginBottom: '1.5rem' }}>Conteúdo gratuito sobre tecnologia e crescimento para pequenos negócios. Sem spam.</p>
          <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'center', flexWrap: 'wrap' }}>
            <input type="email" placeholder="seu@email.com" style={{ padding: '12px 20px', background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '12px', color: '#fff', fontSize: '14px', outline: 'none', minWidth: '260px' }} />
            <button style={{ padding: '12px 28px', background: 'linear-gradient(135deg,#3b82f6,#6366f1)', border: 'none', borderRadius: '12px', color: '#fff', fontWeight: 600, fontSize: '14px', cursor: 'pointer' }}>
              Inscrever-se
            </button>
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
}
