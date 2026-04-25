import { renderMermaidSVG } from "beautiful-mermaid";
import { createHash } from "node:crypto";

const RESPONSIVE_TWEAKS = `
.fd-mermaid svg {
  display: block;
  width: 100%;
  height: auto;
  max-width: 100%;
}
`;

const cache = new Map<string, string>();
const CACHE_LIMIT = 64;

function renderResponsive(chart: string): string {
  const key = createHash("sha1").update(chart).digest("hex");
  const cached = cache.get(key);
  if (cached !== undefined) return cached;

  const raw = renderMermaidSVG(chart, {
    bg: "var(--color-fd-background)",
    fg: "var(--color-fd-foreground)",
    interactive: true,
    transparent: true,
  });
  const responsive = raw
    .replace(/(<svg\b[^>]*?)\swidth="[^"]*"/, "$1")
    .replace(/(<svg\b[^>]*?)\sheight="[^"]*"/, "$1")
    .replace(/<svg\b/, '<svg preserveAspectRatio="xMidYMid meet"');

  if (cache.size >= CACHE_LIMIT) {
    const firstKey = cache.keys().next().value;
    if (firstKey) cache.delete(firstKey);
  }
  cache.set(key, responsive);
  return responsive;
}

export function Mermaid({ chart }: { chart: string }) {
  let html: string;
  try {
    html = renderResponsive(chart);
  } catch {
    return <pre>{chart}</pre>;
  }
  return (
    <div className="fd-mermaid not-prose my-4 w-full overflow-x-auto">
      <style>{RESPONSIVE_TWEAKS}</style>
      <div dangerouslySetInnerHTML={{ __html: html }} />
    </div>
  );
}
