import { createMDX } from "fumadocs-mdx/next";
import { resolve } from "node:path";

const withMDX = createMDX();

/** @type {import('next').NextConfig} */
const config = {
  output: "export",
  reactStrictMode: true,
  trailingSlash: true,
  images: { unoptimized: true },
  basePath: "/spec_to_rest",
  turbopack: {
    root: resolve(import.meta.dirname),
  },
  experimental: {
    optimizePackageImports: ["lucide-react"],
  },
};

export default withMDX(config);
