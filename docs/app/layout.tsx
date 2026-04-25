import { Provider } from "@/components/provider";
import { Banner } from "fumadocs-ui/components/banner";
import type { ReactNode } from "react";
import type { Metadata } from "next";
import "./global.css";

export const metadata: Metadata = {
  metadataBase: new URL("https://hardmax71.github.io/spec_to_rest/"),
  title: {
    template: "%s | spec_to_rest",
    default: "spec_to_rest Documentation",
  },
  description:
    "Documentation for the spec_to_rest compiler — converts formal behavioral specifications into verified REST services.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <Provider>
          <Banner id="prerelease-2026" variant="rainbow">
            Pre-1.0 — APIs and code paths may shift while the M_CE migration lands. See{" "}
            <a
              href="https://github.com/HardMax71/spec_to_rest/issues"
              target="_blank"
              rel="noopener noreferrer"
              className="underline"
            >
              open issues
            </a>{" "}
            for the current roadmap.
          </Banner>
          {children}
        </Provider>
      </body>
    </html>
  );
}
