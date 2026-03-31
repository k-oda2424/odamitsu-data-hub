import type { Metadata } from 'next'
import { Providers } from '@/providers/providers'
import './globals.css'

export const metadata: Metadata = {
  title: 'OdaMitsu Data Hub',
  description: '小田光株式会社 社内データハブ',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="ja">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
