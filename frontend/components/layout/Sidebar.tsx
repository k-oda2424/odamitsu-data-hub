'use client'

import { useMemo } from 'react'
import { usePathname } from 'next/navigation'
import Link from 'next/link'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@/components/ui/sidebar'
import {
  LayoutDashboard,
  Package,
  ShoppingCart,
  Truck,
  FileText,
  BarChart3,
  Globe,
  Database,
  Cog,
  Box,
  ClipboardList,
  Sparkles,
  Receipt,
  Send,
  FileSearch,
  ChevronRight,
  Users,
  ArrowLeftRight,
  BookOpen,
  Link2,
  Diff,
  ShieldCheck,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useAuth } from '@/lib/auth'

interface MenuItem {
  title: string
  icon: LucideIcon
  href: string
  adminOnly?: boolean
}

interface MenuGroup {
  label: string
  icon: LucideIcon
  collapsible: boolean
  items: MenuItem[]
}

const menuGroups: MenuGroup[] = [
  {
    label: 'メイン',
    icon: LayoutDashboard,
    collapsible: false,
    items: [
      { title: 'ダッシュボード', icon: LayoutDashboard, href: '/dashboard' },
    ],
  },
  {
    label: '見積',
    icon: FileText,
    collapsible: true,
    items: [
      { title: '見積一覧', icon: FileText, href: '/estimates' },
      { title: '比較見積', icon: ArrowLeftRight, href: '/estimate-comparisons' },
      { title: 'AI見積取込', icon: Sparkles, href: '/purchase-prices/imports' },
    ],
  },
  {
    label: '経理',
    icon: BarChart3,
    collapsible: true,
    items: [
      { title: '経理業務フロー', icon: ClipboardList, href: '/finance/workflow' },
      { title: '現金出納帳取込', icon: BookOpen, href: '/finance/cashbook-import' },
      { title: '買掛仕入MF変換', icon: BookOpen, href: '/finance/payment-mf-import' },
      { title: '請求書', icon: Receipt, href: '/finance/invoices' },
      { title: '買掛金', icon: BarChart3, href: '/finance/accounts-payable' },
      { title: '買掛帳', icon: BookOpen, href: '/finance/accounts-payable-ledger' },
      { title: '整合性レポート', icon: BookOpen, href: '/finance/accounts-payable-ledger/integrity' },
      { title: 'supplier 累積残', icon: BookOpen, href: '/finance/accounts-payable-ledger/supplier-balances' },
      { title: '前期繰越', icon: BookOpen, href: '/finance/supplier-opening-balance', adminOnly: true },
      { title: '売掛金', icon: BarChart3, href: '/finance/accounts-receivable' },
      { title: 'MF連携状況', icon: Link2, href: '/finance/mf-integration', adminOnly: true },
      { title: 'MF ヘルスチェック', icon: Link2, href: '/finance/mf-health', adminOnly: true },
      { title: 'OFFSET仕訳マスタ', icon: BookOpen, href: '/admin/offset-journal-rule', adminOnly: true },
    ],
  },
  {
    label: '受注',
    icon: FileText,
    collapsible: true,
    items: [
      { title: '受注一覧', icon: FileText, href: '/orders' },
    ],
  },
  {
    label: '仕入・発注',
    icon: ShoppingCart,
    collapsible: true,
    items: [
      { title: '仕入一覧', icon: Receipt, href: '/purchases' },
      { title: '仕入価格一覧', icon: Receipt, href: '/purchase-prices' },
      { title: '仕入価格変更一覧', icon: ClipboardList, href: '/purchase-prices/changes' },
      { title: '仕入価格変更一括入力', icon: ClipboardList, href: '/purchase-prices/changes/bulk-input' },
      { title: '仕入先見積データ', icon: FileSearch, href: '/purchase-prices/quotes' },
      { title: '発注一覧', icon: Send, href: '/send-orders' },
      { title: '発注入力', icon: Truck, href: '/send-orders/create' },
    ],
  },
  {
    label: '商品',
    icon: Package,
    collapsible: true,
    items: [
      { title: '在庫一覧', icon: Box, href: '/stock' },
      { title: '商品マスタ', icon: Package, href: '/goods' },
      { title: '販売商品マスタ', icon: ShoppingCart, href: '/sales-goods' },
      { title: '販売商品ワーク', icon: ClipboardList, href: '/sales-goods/work' },
      { title: '得意先商品', icon: ClipboardList, href: '/partner-goods' },
    ],
  },
  {
    label: 'B-CART',
    icon: Globe,
    collapsible: true,
    items: [
      { title: 'B-CART出荷情報入力', icon: Truck, href: '/bcart/shipping' },
      { title: 'B-CARTカテゴリ', icon: Database, href: '/bcart/categories' },
      { title: 'B-CART商品', icon: Package, href: '/bcart/products' },
      { title: 'B-CART変更点一覧', icon: Diff, href: '/bcart/pending-changes' },
    ],
  },
  {
    label: 'マスタ管理',
    icon: Database,
    collapsible: true,
    items: [
      { title: '仕入先', icon: Database, href: '/masters/suppliers' },
      { title: 'メーカー', icon: Database, href: '/masters/makers' },
      { title: '倉庫', icon: Database, href: '/masters/warehouses' },
      { title: '得意先', icon: ClipboardList, href: '/masters/partners' },
      { title: '連携ファイル設定', icon: FileText, href: '/masters/linked-files' },
      { title: 'ユーザー管理', icon: Users, href: '/masters/users', adminOnly: true },
      { title: '監査ログ', icon: ShieldCheck, href: '/admin/audit-log', adminOnly: true },
      { title: 'バッチ管理', icon: Cog, href: '/batch' },
    ],
  },
]

const allHrefs = menuGroups.flatMap((g) => g.items.map((i) => i.href))

function isMenuActive(pathname: string, href: string): boolean {
  if (pathname === href) return true
  if (!pathname.startsWith(href + '/')) return false
  const hasMoreSpecificMatch = allHrefs.some(
    (other) => other !== href && other.startsWith(href + '/') && (pathname === other || pathname.startsWith(other + '/'))
  )
  return !hasMoreSpecificMatch
}

function isGroupActive(pathname: string, items: MenuItem[]): boolean {
  return items.some((item) => isMenuActive(pathname, item.href))
}

export function AppSidebar() {
  const pathname = usePathname()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const filteredGroups = useMemo(
    () => menuGroups.map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.adminOnly || isAdmin),
    })),
    [isAdmin],
  )

  return (
    <Sidebar>
      <SidebarHeader className="border-b px-4 py-3">
        <div className="flex items-center gap-2">
          <div className="flex h-7 w-7 items-center justify-center rounded-md bg-primary text-[11px] font-bold text-primary-foreground">
            OD
          </div>
          <div>
            <h2 className="text-sm font-semibold leading-none">OdaMitsu</h2>
            <p className="text-[10px] text-muted-foreground leading-none mt-0.5">Data Hub</p>
          </div>
        </div>
      </SidebarHeader>
      <SidebarContent>
        {filteredGroups.map((group) => {
          if (!group.collapsible) {
            return (
              <SidebarGroup key={group.label}>
                <SidebarGroupContent>
                  <SidebarMenu>
                    {group.items.map((item) => (
                      <SidebarMenuItem key={item.href}>
                        <SidebarMenuButton asChild isActive={isMenuActive(pathname, item.href)}>
                          <Link href={item.href}>
                            <item.icon className="h-4 w-4" />
                            <span>{item.title}</span>
                          </Link>
                        </SidebarMenuButton>
                      </SidebarMenuItem>
                    ))}
                  </SidebarMenu>
                </SidebarGroupContent>
              </SidebarGroup>
            )
          }

          const active = isGroupActive(pathname, group.items)

          return (
            <Collapsible key={group.label} defaultOpen={active} className="group/collapsible">
              <SidebarGroup>
                <SidebarGroupLabel asChild>
                  <CollapsibleTrigger className="flex w-full items-center justify-between">
                    <span className="flex items-center gap-2">
                      <group.icon className="h-3.5 w-3.5" />
                      {group.label}
                    </span>
                    <ChevronRight className="h-3.5 w-3.5 transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90" />
                  </CollapsibleTrigger>
                </SidebarGroupLabel>
                <CollapsibleContent>
                  <SidebarGroupContent>
                    <SidebarMenu>
                      {group.items.map((item) => (
                        <SidebarMenuItem key={item.href}>
                          <SidebarMenuButton asChild isActive={isMenuActive(pathname, item.href)}>
                            <Link href={item.href}>
                              <item.icon className="h-4 w-4" />
                              <span>{item.title}</span>
                            </Link>
                          </SidebarMenuButton>
                        </SidebarMenuItem>
                      ))}
                    </SidebarMenu>
                  </SidebarGroupContent>
                </CollapsibleContent>
              </SidebarGroup>
            </Collapsible>
          )
        })}
      </SidebarContent>
    </Sidebar>
  )
}
