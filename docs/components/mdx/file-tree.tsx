import type { ReactNode } from "react";
import {
  File as FileIcon,
  Folder as FolderIcon,
  ChevronRight,
} from "lucide-react";

interface BaseRowProps {
  name: ReactNode;
  note?: ReactNode;
  className?: string;
}

interface FileRowProps extends BaseRowProps {
  icon?: ReactNode;
}

interface FolderRowProps extends BaseRowProps {
  children?: ReactNode;
}

interface DetailsProps extends BaseRowProps {
  icon?: ReactNode;
  children?: ReactNode;
}

const rowClass =
  "grid grid-cols-[auto_1fr_auto] items-baseline gap-x-3 rounded-md px-2 py-1.5 text-sm hover:bg-fd-accent/40 [&_svg]:size-4 [&_svg]:self-center [&_svg]:text-fd-muted-foreground";

const summaryClass =
  rowClass +
  " cursor-pointer select-none list-none transition-colors [&::-webkit-details-marker]:hidden";

const chevronClass =
  "shrink-0 transition-transform group-open:rotate-90";

export function FileTree({
  children,
  className,
}: {
  children?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={`not-prose rounded-md border bg-fd-card p-2 ${className ?? ""}`}
    >
      {children}
    </div>
  );
}

export function FileTreeRow({ name, note, icon, className }: FileRowProps) {
  return (
    <div className={`${rowClass} ${className ?? ""}`}>
      {icon ?? <FileIcon />}
      <span className="font-mono text-[0.8125rem]">{name}</span>
      {note && (
        <span className="text-xs text-fd-muted-foreground text-right">{note}</span>
      )}
    </div>
  );
}

export function FileTreeFolder({
  name,
  note,
  children,
  className,
}: FolderRowProps) {
  return (
    <div className={className}>
      <div className={rowClass}>
        <FolderIcon />
        <span className="font-mono text-[0.8125rem] font-medium">{name}</span>
        {note && (
          <span className="text-xs text-fd-muted-foreground text-right">{note}</span>
        )}
      </div>
      {children && (
        <div className="ms-2 flex flex-col border-l ps-2">{children}</div>
      )}
    </div>
  );
}

export function FileTreeDetails({
  name,
  note,
  icon,
  children,
  className,
}: DetailsProps) {
  return (
    <details className={`group ${className ?? ""}`}>
      <summary className={summaryClass}>
        <span className="flex items-center gap-1">
          <ChevronRight className={chevronClass} />
          {icon ?? <FileIcon />}
        </span>
        <span className="font-mono text-[0.8125rem]">{name}</span>
        {note && (
          <span className="text-xs text-fd-muted-foreground text-right">{note}</span>
        )}
      </summary>
      <div className="ms-7 my-2 flex flex-col gap-2 border-l-2 border-fd-primary/30 ps-3">
        {children}
      </div>
    </details>
  );
}
