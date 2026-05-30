import type { BaseLayoutProps } from "fumadocs-ui/layouts/shared";

export function baseOptions(): BaseLayoutProps {
  return {
    nav: {
      title: (
        <span style={{ display: "inline-flex", alignItems: "center", gap: "0.45rem" }}>
          <svg width="26" height="21" viewBox="0 0 120 96" fill="none" aria-hidden="true">
            <g stroke="currentColor" strokeWidth="11" strokeLinecap="round" strokeLinejoin="round">
              <path d="M34 20 V76" />
              <path d="M34 48 H78" />
            </g>
            <path
              d="M64 31 L90 48 L64 65"
              stroke="#6366f1"
              strokeWidth="11"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
          spec_to_rest
        </span>
      ),
    },
    githubUrl: "https://github.com/HardMax71/spec_to_rest",
  };
}
