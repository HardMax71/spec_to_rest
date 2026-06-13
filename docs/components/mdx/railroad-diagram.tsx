import fs from "node:fs";
import path from "node:path";

const cache = new Map<string, string>();

function readSvg(name: string): string {
  const cached = cache.get(name);
  if (cached !== undefined) return cached;
  const file = path.join(process.cwd(), "public", "grammar", `${name}.svg`);
  const svg = fs.readFileSync(file, "utf8");
  cache.set(name, svg);
  return svg;
}

interface Props {
  name: string;
  alt: string;
}

export function RailroadDiagram({ name, alt }: Props) {
  const svg = readSvg(name);
  return (
    <div
      className="fd-railroad not-prose my-4 max-h-[80vh] overflow-auto"
      role="img"
      aria-label={alt}
    >
      <div dangerouslySetInnerHTML={{ __html: svg }} />
    </div>
  );
}
