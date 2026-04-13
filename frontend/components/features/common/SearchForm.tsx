import { type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Search, RotateCcw } from 'lucide-react'

interface SearchFormProps {
  children: ReactNode
  onSearch: () => void
  onReset: () => void
}

export function SearchForm({ children, onSearch, onReset }: SearchFormProps) {
  return (
    <form
      className="rounded-lg border bg-card p-4 shadow-sm"
      onSubmit={(e) => { e.preventDefault(); onSearch() }}
    >
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {children}
      </div>
      <div className="mt-4 flex gap-2 border-t pt-3">
        <Button type="submit" size="sm">
          <Search className="mr-1.5 h-3.5 w-3.5" />
          検索
        </Button>
        <Button type="button" variant="ghost" onClick={onReset} size="sm">
          <RotateCcw className="mr-1.5 h-3.5 w-3.5" />
          リセット
        </Button>
      </div>
    </form>
  )
}
