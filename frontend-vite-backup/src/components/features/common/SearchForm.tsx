import { type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Search, RotateCcw } from 'lucide-react'

interface SearchFormProps {
  children: ReactNode
  onSearch: () => void
  onReset: () => void
}

export function SearchForm({ children, onSearch, onReset }: SearchFormProps) {
  return (
    <Card>
      <CardContent className="pt-6">
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          {children}
        </div>
        <div className="mt-4 flex gap-2">
          <Button onClick={onSearch} size="sm">
            <Search className="mr-2 h-4 w-4" />
            検索
          </Button>
          <Button variant="outline" onClick={onReset} size="sm">
            <RotateCcw className="mr-2 h-4 w-4" />
            リセット
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
