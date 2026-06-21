import { Geist, Geist_Mono } from 'next/font/google'
import type { ReactNode } from 'react'

/**
 * Layout do SITE PÚBLICO do CMS (/p/**). Carrega a fonte Geist e expõe `--font-geist-sans` à árvore
 * pública — usada pelo preset de tema 'meada-dark' (cmsShellStyle injeta fontFamily Geist). Sites sem
 * preset não dependem disto (o body herda a fonte do tema genérico). Sem chrome aqui — cada página
 * pública é renderizada pelo CmsRender.
 */
const geistSans = Geist({ variable: '--font-geist-sans', subsets: ['latin'], display: 'swap' })
const geistMono = Geist_Mono({ variable: '--font-geist-mono', subsets: ['latin'], display: 'swap' })

export default function PublicSiteLayout({ children }: { children: ReactNode }) {
  return <div className={`${geistSans.variable} ${geistMono.variable}`}>{children}</div>
}
