import { source } from "@/lib/source";

export const revalidate = false;

export function GET() {
  const pages = source.getPages();
  const sections = pages.map((page) => {
    return `# ${page.data.title} (${page.url})\n\n${page.data.description ?? ""}`;
  });

  return new Response(sections.join("\n\n---\n\n"), {
    headers: { "Content-Type": "text/plain; charset=utf-8" },
  });
}
