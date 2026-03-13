'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

export default function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const pathname = usePathname();

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const navLinks = [
    { href: '/servicos', label: 'Serviços' },
    { href: '/produtos', label: 'Produtos' },
    { href: '/sobre', label: 'Sobre' },
    { href: '/contato', label: 'Contato' },
  ];

  return (
    <nav
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        zIndex: 100,
        height: '72px',
        background: scrolled ? 'rgba(0,8,18,0.85)' : 'rgba(0,8,18,0.5)',
        backdropFilter: 'blur(20px)',
        WebkitBackdropFilter: 'blur(20px)',
        borderBottom: scrolled ? '1px solid rgba(59,130,246,0.15)' : '1px solid rgba(59,130,246,0.05)',
        transition: 'background 0.3s ease, border-color 0.3s ease',
      }}
    >
      <div
        style={{
          maxWidth: '1360px',
          margin: '0 auto',
          padding: '0 2rem',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        {/* Logo */}
        <Link href="/" style={{ textDecoration: 'none', display: 'flex', alignItems: 'center', gap: '11px' }}>
          <svg width="38" height="38" viewBox="0 0 38 38" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <linearGradient id="navBg" x1="0" y1="0" x2="38" y2="38" gradientUnits="userSpaceOnUse">
                <stop stopColor="#1d4ed8"/>
                <stop offset="0.55" stopColor="#6d28d9"/>
                <stop offset="1" stopColor="#4c1d95"/>
              </linearGradient>
              <radialGradient id="navGlow" cx="25%" cy="20%" r="65%">
                <stop offset="0%" stopColor="white" stopOpacity="0.18"/>
                <stop offset="100%" stopColor="white" stopOpacity="0"/>
              </radialGradient>
            </defs>
            <rect width="38" height="38" rx="11" fill="url(#navBg)"/>
            <rect width="38" height="38" rx="11" fill="url(#navGlow)"/>
            <path d="M8.5 27.5 L9.5 12 L19 21.5 L28.5 12 L29.5 27.5"
              stroke="white" strokeWidth="2.75" strokeLinecap="round" strokeLinejoin="round" fill="none"/>
            <circle cx="19" cy="21.5" r="2" fill="white" fillOpacity="0.55"/>
          </svg>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0px' }}>
            <span style={{ fontSize: '20px', fontWeight: 800, color: '#fff', letterSpacing: '-0.5px', lineHeight: 1 }}>
              Meada
            </span>
            <span style={{ fontSize: '9.5px', fontWeight: 600, color: 'rgba(148,163,184,0.7)', letterSpacing: '0.12em', textTransform: 'uppercase', lineHeight: 1.4 }}>
              Digital
            </span>
          </div>
        </Link>

        {/* Nav Links */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '2rem' }}>
          {navLinks.map((link) => {
            const isActive = pathname === link.href;
            return (
              <Link
                key={link.href}
                href={link.href}
                style={{
                  textDecoration: 'none',
                  color: isActive ? '#fff' : 'rgba(255,255,255,0.65)',
                  fontSize: '15px',
                  fontWeight: isActive ? 600 : 400,
                  paddingBottom: '4px',
                  borderBottom: isActive ? '2px solid #3b82f6' : '2px solid transparent',
                  transition: 'color 0.2s ease, border-color 0.2s ease',
                }}
                onMouseEnter={(e) => {
                  if (!isActive) {
                    (e.target as HTMLElement).style.color = '#fff';
                  }
                }}
                onMouseLeave={(e) => {
                  if (!isActive) {
                    (e.target as HTMLElement).style.color = 'rgba(255,255,255,0.65)';
                  }
                }}
              >
                {link.label}
              </Link>
            );
          })}

        </div>
      </div>
    </nav>
  );
}
