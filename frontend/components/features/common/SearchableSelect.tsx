'use client'

import { useState } from 'react'
import { CheckIcon, ChevronsUpDown, X } from 'lucide-react'
import { cn, normalizeForSearch } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'

interface SearchableSelectProps {
  value: string
  onValueChange: (value: string) => void
  options: { value: string; label: string }[]
  placeholder?: string
  searchPlaceholder?: string
  emptyMessage?: string
  disabled?: boolean
  clearable?: boolean
}

export function SearchableSelect({
  value,
  onValueChange,
  options,
  placeholder = '選択してください',
  searchPlaceholder = '検索...',
  emptyMessage = '見つかりません',
  disabled = false,
  clearable = true,
}: SearchableSelectProps) {
  const [open, setOpen] = useState(false)

  const selectedLabel = options.find((opt) => opt.value === value)?.label

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          disabled={disabled}
          className={cn(
            'w-full justify-between font-normal',
            !selectedLabel && 'text-muted-foreground'
          )}
        >
          <span className="truncate">
            {selectedLabel ?? placeholder}
          </span>
          <span className="flex shrink-0 items-center gap-1">
            {clearable && value && !disabled && (
              <span
                role="button"
                tabIndex={0}
                aria-label="選択をクリア"
                className="rounded-sm hover:bg-accent cursor-pointer"
                onClick={(e) => {
                  e.stopPropagation()
                  onValueChange('')
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.stopPropagation()
                    e.preventDefault()
                    onValueChange('')
                  }
                }}
              >
                <X className="size-4 opacity-50 hover:opacity-100" />
              </span>
            )}
            <ChevronsUpDown className="size-4 opacity-50" />
          </span>
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
        <Command filter={(value, search) => {
          const nv = normalizeForSearch(value.toLowerCase())
          const ns = normalizeForSearch(search.toLowerCase())
          return nv.includes(ns) ? 1 : 0
        }}>
          <CommandInput placeholder={searchPlaceholder} />
          <CommandList>
            <CommandEmpty>{emptyMessage}</CommandEmpty>
            <CommandGroup>
              {options.map((option) => (
                <CommandItem
                  key={option.value}
                  value={`${option.value} ${option.label}`}
                  onSelect={() => {
                    if (option.value === value && !clearable) {
                      setOpen(false)
                      return
                    }
                    onValueChange(option.value === value ? '' : option.value)
                    setOpen(false)
                  }}
                >
                  <CheckIcon
                    className={cn(
                      'size-4',
                      value === option.value ? 'opacity-100' : 'opacity-0'
                    )}
                  />
                  {option.label}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
