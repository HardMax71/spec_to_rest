import { Provider } from "@/components/provider";
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
        <Provider>{children}</Provider>
      </body>
    </html>
  );
}
