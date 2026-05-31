import { source } from "@/lib/source";
import {
  DocsBody,
  DocsDescription,
  DocsPage,
  DocsTitle,
  EditOnGitHub,
} from "fumadocs-ui/page";
import { notFound } from "next/navigation";
import { getMDXComponents } from "@/components/mdx";
import { getGitLastModified } from "@/lib/git-timestamp";
import type { Metadata } from "next";

const REPO_OWNER = "HardMax71";
const REPO_NAME = "spec_to_rest";
const REPO_BRANCH = "main";

export default async function Page(props: {
  params: Promise<{ slug?: string[] }>;
}) {
  const params = await props.params;
  const page = source.getPage(params.slug);
  if (!page) notFound();

  const isHome = !params.slug || params.slug.length === 0;
  const MDX = page.data.body;
  const repoRel = `docs/content/docs/${page.path}`;
  const lastModified = getGitLastModified(repoRel);

  const editUrl = `https://github.com/${REPO_OWNER}/${REPO_NAME}/edit/${REPO_BRANCH}/${repoRel}`;

  const lastUpdatedLabel = lastModified
    ? lastModified.toLocaleDateString(undefined, {
        year: "numeric",
        month: "long",
        day: "numeric",
      })
    : null;

  return (
    <DocsPage toc={page.data.toc} full={page.data.full}>
      {!isHome && (
        <>
          <div className="flex items-start justify-between gap-4">
            <DocsTitle>{page.data.title}</DocsTitle>
            <EditOnGitHub href={editUrl} className="shrink-0 mt-1" />
          </div>
          <DocsDescription>{page.data.description}</DocsDescription>
          {lastUpdatedLabel && (
            <p className="-mt-2 text-xs text-fd-muted-foreground">
              Last updated:{" "}
              <time dateTime={lastModified?.toISOString()}>{lastUpdatedLabel}</time>
            </p>
          )}
        </>
      )}
      <DocsBody>
        <MDX components={getMDXComponents()} />
      </DocsBody>
    </DocsPage>
  );
}

export function generateStaticParams() {
  return source.generateParams();
}

export async function generateMetadata(props: {
  params: Promise<{ slug?: string[] }>;
}): Promise<Metadata> {
  const params = await props.params;
  const page = source.getPage(params.slug);
  if (!page) notFound();

  return {
    title: page.data.title,
    description: page.data.description,
  };
}
