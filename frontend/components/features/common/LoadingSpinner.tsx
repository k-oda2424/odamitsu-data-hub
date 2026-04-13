import { Loader2 } from 'lucide-react'

interface LoadingSpinnerProps {
  message?: string
}

export function LoadingSpinner({ message }: LoadingSpinnerProps) {
  return (
    <div className="flex flex-col items-center justify-center py-16 gap-3">
      <Loader2 className="h-5 w-5 animate-spin text-muted-foreground/60" />
      {message && <p className="text-xs text-muted-foreground">{message}</p>}
    </div>
  )
}
