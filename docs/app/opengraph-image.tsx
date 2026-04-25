import { ImageResponse } from "next/og";

export const dynamic = "force-static";
export const alt = "spec_to_rest documentation";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          height: "100%",
          width: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          background:
            "linear-gradient(135deg, #0f172a 0%, #1e3a8a 60%, #6d28d9 100%)",
          color: "#ffffff",
          padding: "72px",
          fontFamily: "system-ui, sans-serif",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", fontSize: 28, opacity: 0.85 }}>
          spec_to_rest <span style={{ marginLeft: 16, opacity: 0.6 }}>·</span>
          <span style={{ marginLeft: 16 }}>documentation</span>
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
          <div style={{ fontSize: 80, fontWeight: 700, lineHeight: 1.05, letterSpacing: "-0.02em" }}>
            spec_to_rest
          </div>
          <div style={{ fontSize: 36, opacity: 0.85, lineHeight: 1.3 }}>
            Compile formal behavioral specs into verified REST services
          </div>
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: 22, opacity: 0.7 }}>
          <span>Scala 3 · Cats Effect · Z3 · Alloy</span>
          <span>github.com/HardMax71/spec_to_rest</span>
        </div>
      </div>
    ),
    size,
  );
}
