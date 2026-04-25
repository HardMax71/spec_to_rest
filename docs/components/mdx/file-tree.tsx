"use client";
import { useState } from "react";
import type { ReactNode } from "react";
import {
  File as FileIcon,
  Folder as FolderIcon,
  FolderOpen as FolderOpenIcon,
  ChevronRight,
} from "lucide-react";
import { DynamicCodeBlock } from "fumadocs-ui/components/dynamic-codeblock";

interface BaseRowProps {
  name: ReactNode;
  note?: ReactNode;
  className?: string;
}

interface FileRowProps extends BaseRowProps {
  icon?: ReactNode;
  description?: string;
  source?: string;
  sourceLang?: string;
}

interface FolderRowProps extends BaseRowProps {
  children?: ReactNode;
  defaultOpen?: boolean;
}

const rowClass =
  "grid grid-cols-[auto_1fr_auto] items-baseline gap-x-3 rounded-md px-2 py-1.5 text-sm hover:bg-fd-accent/40 [&_svg]:size-4 [&_svg]:self-center [&_svg]:text-fd-muted-foreground";

const interactiveRowClass =
  rowClass + " w-full text-left cursor-pointer transition-colors";

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

export function FileTreeRow({
  name,
  note,
  icon,
  description,
  source,
  sourceLang,
  className,
}: FileRowProps) {
  const interactive = Boolean(description) || Boolean(source);
  const [open, setOpen] = useState(false);

  if (!interactive) {
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

  return (
    <div className={className}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className={interactiveRowClass}
      >
        <span className="flex items-center gap-1">
          <ChevronRight
            className={`shrink-0 transition-transform ${open ? "rotate-90" : ""}`}
          />
          {icon ?? <FileIcon />}
        </span>
        <span className="font-mono text-[0.8125rem]">{name}</span>
        {note && (
          <span className="text-xs text-fd-muted-foreground text-right">{note}</span>
        )}
      </button>
      {open && (
        <div className="ms-7 my-2 flex flex-col gap-3 border-l-2 border-fd-primary/30 ps-3">
          {description && (
            <p className="text-sm text-fd-muted-foreground">{description}</p>
          )}
          {source && (
            <div className="not-prose">
              <DynamicCodeBlock lang={sourceLang ?? "text"} code={source} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function FileTreeFolder({
  name,
  note,
  children,
  className,
  defaultOpen = true,
}: FolderRowProps) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className={className}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className={interactiveRowClass}
      >
        <span className="flex items-center gap-1">
          <ChevronRight
            className={`shrink-0 transition-transform ${open ? "rotate-90" : ""}`}
          />
          {open ? <FolderOpenIcon /> : <FolderIcon />}
        </span>
        <span className="font-mono text-[0.8125rem] font-medium">{name}</span>
        {note && (
          <span className="text-xs text-fd-muted-foreground text-right">{note}</span>
        )}
      </button>
      {open && children && (
        <div className="ms-2 flex flex-col border-l ps-2">{children}</div>
      )}
    </div>
  );
}
