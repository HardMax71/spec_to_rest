import { createMDX } from "fumadocs-mdx/next";
import { resolve } from "node:path";

const withMDX = createMDX();

/** @type {import('next').NextConfig} */
const config = {
  output: "standalone",
  reactStrictMode: true,
  images: { unoptimized: true },
  turbopack: {
    root: resolve(import.meta.dirname),
  },
  experimental: {
    optimizePackageImports: ["lucide-react"],
  },
};

export default withMDX(config);
