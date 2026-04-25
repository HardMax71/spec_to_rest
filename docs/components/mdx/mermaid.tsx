import { renderMermaidSVG } from "beautiful-mermaid";

const RESPONSIVE_TWEAKS = `
.fd-mermaid svg {
  display: block;
  width: 100%;
  height: auto;
  max-width: 100%;
}
`;

export async function Mermaid({ chart }: { chart: string }) {
  try {
    const raw = renderMermaidSVG(chart, {
      bg: "var(--color-fd-background)",
      fg: "var(--color-fd-foreground)",
      interactive: true,
      transparent: true,
    });
    const responsive = raw
      .replace(/(<svg\b[^>]*?)\swidth="[^"]*"/, "$1")
      .replace(/(<svg\b[^>]*?)\sheight="[^"]*"/, "$1")
      .replace(
        /<svg\b/,
        '<svg preserveAspectRatio="xMidYMid meet"'
      );
    return (
      <div className="fd-mermaid not-prose my-4 w-full overflow-x-auto">
        <style>{RESPONSIVE_TWEAKS}</style>
        <div dangerouslySetInnerHTML={{ __html: responsive }} />
      </div>
    );
  } catch {
    return <pre>{chart}</pre>;
  }
}
