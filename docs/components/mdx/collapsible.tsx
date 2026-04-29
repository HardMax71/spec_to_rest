import { ChevronRight } from "lucide-react";
import type { ReactNode } from "react";

interface Props {
  title: string;
  defaultOpen?: boolean;
  children: ReactNode;
}

export function Collapsible({ title, defaultOpen = false, children }: Props) {
  return (
    <details
      className="fd-collapsible my-4 rounded-lg border bg-fd-card overflow-hidden [&[open]>summary>.fd-collapsible-chevron]:rotate-90"
      {...(defaultOpen ? { open: true } : {})}
    >
      <summary className="flex cursor-pointer select-none list-none items-center gap-2 px-4 py-3 text-sm font-medium hover:bg-fd-accent [&::-webkit-details-marker]:hidden">
        <ChevronRight
          aria-hidden="true"
          className="fd-collapsible-chevron h-4 w-4 shrink-0 transition-transform duration-150"
        />
        <span>{title}</span>
      </summary>
      <div className="border-t bg-fd-background/40 px-4 py-3 [&>:first-child]:mt-0 [&>:last-child]:mb-0">
        {children}
      </div>
    </details>
  );
}
