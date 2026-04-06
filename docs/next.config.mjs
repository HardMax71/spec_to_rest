import { createMDX } from "fumadocs-mdx/next";
import { resolve } from "node:path";

const withMDX = createMDX();

const isProd = process.env.NODE_ENV === "production";

/** @type {import('next').NextConfig} */
const config = {
  output: "export",
  reactStrictMode: true,
  trailingSlash: true,
  images: { unoptimized: true },
  basePath: isProd ? "/spec_to_rest" : "",
  turbopack: {
    root: resolve(import.meta.dirname),
  },
};

export default withMDX(config);
