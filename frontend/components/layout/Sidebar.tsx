'use client'

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
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

interface MenuItem {
  title: string
  icon: LucideIcon
  href: string
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
    label: '商品・在庫',
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
    label: '受注・発注',
    icon: FileText,
    collapsible: true,
    items: [
      { title: '受注一覧', icon: FileText, href: '/orders' },
      { title: '得意先発注', icon: Truck, href: '/partner-orders' },
    ],
  },
  {
    label: '仕入',
    icon: ShoppingCart,
    collapsible: true,
    items: [
      { title: '仕入入力', icon: ShoppingCart, href: '/purchases' },
      { title: '発注一覧', icon: Send, href: '/send-orders' },
      { title: '発注入力', icon: Truck, href: '/send-orders/create' },
      { title: '仕入価格一覧', icon: Receipt, href: '/purchase-prices' },
      { title: '仕入価格変更一覧', icon: ClipboardList, href: '/purchase-prices/changes' },
      { title: '仕入価格変更一括入力', icon: ClipboardList, href: '/purchase-prices/changes/bulk-input' },
      { title: 'AI見積取込', icon: Sparkles, href: '/purchase-prices/imports' },
      { title: '仕入先見積データ', icon: FileSearch, href: '/purchase-prices/quotes' },
    ],
  },
  {
    label: '見積・財務',
    icon: BarChart3,
    collapsible: true,
    items: [
      { title: '見積一覧', icon: FileText, href: '/estimates' },
      { title: '買掛金', icon: BarChart3, href: '/finance/accounts-payable' },
      { title: '請求書', icon: Receipt, href: '/finance/invoices' },
    ],
  },
  {
    label: '外部連携・マスタ',
    icon: Globe,
    collapsible: true,
    items: [
      { title: 'B-CART出荷', icon: Globe, href: '/bcart/shipping' },
      { title: 'B-CARTカテゴリ', icon: Database, href: '/bcart/categories' },
      { title: 'メーカー', icon: Database, href: '/masters/makers' },
      { title: '倉庫', icon: Database, href: '/masters/warehouses' },
      { title: '連携ファイル設定', icon: FileText, href: '/masters/linked-files' },
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
        {menuGroups.map((group) => {
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
