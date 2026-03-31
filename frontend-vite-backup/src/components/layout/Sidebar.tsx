import { useLocation, Link } from 'react-router-dom'
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
  Box,
  ClipboardList,
} from 'lucide-react'

const menuGroups = [
  {
    label: 'メイン',
    items: [
      { title: 'ダッシュボード', icon: LayoutDashboard, href: '/dashboard' },
    ],
  },
  {
    label: '商品・在庫',
    items: [
      { title: '在庫一覧', icon: Box, href: '/stock' },
      { title: '商品マスタ', icon: Package, href: '/goods' },
      { title: '販売商品', icon: ShoppingCart, href: '/sales-goods' },
      { title: '得意先商品', icon: ClipboardList, href: '/partner-goods' },
    ],
  },
  {
    label: '受注・発注',
    items: [
      { title: '受注一覧', icon: FileText, href: '/orders' },
      { title: '得意先発注', icon: Truck, href: '/partner-orders' },
    ],
  },
  {
    label: '仕入',
    items: [
      { title: '仕入入力', icon: ShoppingCart, href: '/purchases' },
      { title: '発注入力', icon: Truck, href: '/send-orders' },
      { title: '仕入単価', icon: FileText, href: '/purchase-prices' },
    ],
  },
  {
    label: '見積',
    items: [
      { title: '見積一覧', icon: ClipboardList, href: '/estimates' },
    ],
  },
  {
    label: '財務',
    items: [
      { title: '買掛金', icon: BarChart3, href: '/finance/accounts-payable' },
      { title: '請求書', icon: FileText, href: '/finance/invoices' },
    ],
  },
  {
    label: '外部連携',
    items: [
      { title: 'B-CART出荷', icon: Globe, href: '/bcart/shipping' },
    ],
  },
  {
    label: 'マスタ',
    items: [
      { title: 'マスタ管理', icon: Database, href: '/masters' },
    ],
  },
]

export function AppSidebar() {
  const location = useLocation()

  return (
    <Sidebar>
      <SidebarHeader className="border-b px-4 py-3">
        <h2 className="text-lg font-semibold">OdaMitsu Data Hub</h2>
      </SidebarHeader>
      <SidebarContent>
        {menuGroups.map((group) => (
          <SidebarGroup key={group.label}>
            <SidebarGroupLabel>{group.label}</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {group.items.map((item) => (
                  <SidebarMenuItem key={item.href}>
                    <SidebarMenuButton
                      asChild
                      isActive={location.pathname === item.href}
                    >
                      <Link to={item.href}>
                        <item.icon className="h-4 w-4" />
                        <span>{item.title}</span>
                      </Link>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>
    </Sidebar>
  )
}
