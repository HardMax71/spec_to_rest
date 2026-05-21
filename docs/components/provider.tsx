"use client";

import { RootProvider } from "fumadocs-ui/provider/next";
import type { ReactNode } from "react";

export function Provider({ children }: { children: ReactNode }) {
  return (
    <RootProvider
      search={{
        options: {
          type: "static",
          api: "/spec_to_rest/api/search",
        },
      }}
    >
      {children}
    </RootProvider>
  );
}
