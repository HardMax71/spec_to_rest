import { Children } from "react";
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
  description?: string;
  children?: ReactNode;
}

const rowClass =
  "grid grid-cols-[auto_1fr_auto] items-center gap-x-3 rounded-md px-2 py-1.5 text-sm hover:bg-fd-accent/40 [&_svg]:size-4 [&_svg]:text-fd-muted-foreground";

const summaryClass =
  rowClass +
  " cursor-pointer select-none list-none transition-colors [&::-webkit-details-marker]:hidden";

const chevronClass = "fd-tree-chevron shrink-0";

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
  if (Children.count(children) === 0) {
    return (
      <div className={className}>
        <div className={rowClass}>
          <FolderIcon />
          <span className="font-mono text-[0.8125rem] font-medium">{name}</span>
          {note && (
            <span className="text-xs text-fd-muted-foreground text-right">
              {note}
            </span>
          )}
        </div>
      </div>
    );
  }
  return (
    <details open className={`group ${className ?? ""}`}>
      <summary className={summaryClass}>
        <span className="flex items-center gap-1">
          <ChevronRight className={chevronClass} />
          <FolderIcon />
        </span>
        <span className="font-mono text-[0.8125rem] font-medium">{name}</span>
        {note && (
          <span className="text-xs text-fd-muted-foreground text-right">{note}</span>
        )}
      </summary>
      <div className="ms-2 flex flex-col border-l ps-2">{children}</div>
    </details>
  );
}

export function FileTreeDetails({
  name,
  note,
  icon,
  description,
  children,
  className,
}: DetailsProps) {
  const hasContent = Boolean(description) || Children.count(children) > 0;
  if (!hasContent) {
    return (
      <FileTreeRow name={name} note={note} icon={icon} className={className} />
    );
  }
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
      {description && (
        <p className="ms-9 mt-1.5 mb-1 text-sm font-sans text-fd-foreground">
          {description}
        </p>
      )}
      {children && Children.count(children) > 0 && (
        <div className="ms-2 my-2 border-l ps-2 [&_figure]:my-0">
          {children}
        </div>
      )}
    </details>
  );
}
