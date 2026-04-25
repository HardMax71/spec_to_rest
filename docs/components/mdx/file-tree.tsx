import type { ReactNode } from "react";
import { File as FileIcon, Folder as FolderIcon } from "lucide-react";

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

const rowClass =
  "grid grid-cols-[auto_1fr_auto] items-baseline gap-x-3 rounded-md px-2 py-1.5 text-sm hover:bg-fd-accent/40 [&_svg]:size-4 [&_svg]:self-center [&_svg]:text-fd-muted-foreground";

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
