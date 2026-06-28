import { renderMermaidSVG } from "beautiful-mermaid";
import { createHash } from "node:crypto";

const cache = new Map<string, string>();
const CACHE_LIMIT = 64;

function render(chart: string): string {
  const key = createHash("sha1").update(chart).digest("hex");
  const cached = cache.get(key);
  if (cached !== undefined) return cached;

  const svg = renderMermaidSVG(chart, {
    bg: "var(--color-fd-background)",
    fg: "var(--color-fd-foreground)",
    surface: "var(--color-fd-card)",
    border: "var(--color-fd-muted-foreground)",
    line: "var(--color-fd-muted-foreground)",
    accent: "var(--color-fd-primary)",
    muted: "var(--color-fd-muted-foreground)",
    transparent: true,
  });

  if (cache.size >= CACHE_LIMIT) {
    const firstKey = cache.keys().next().value;
    if (firstKey) cache.delete(firstKey);
  }
  cache.set(key, svg);
  return svg;
}

export function Mermaid({ chart }: { chart: string }) {
  let html: string;
  try {
    html = render(chart);
  } catch {
    return <pre>{chart}</pre>;
  }
  return (
    <div className="fd-mermaid not-prose my-4 max-h-[80vh] overflow-auto">
      <div dangerouslySetInnerHTML={{ __html: html }} />
    </div>
  );
}
