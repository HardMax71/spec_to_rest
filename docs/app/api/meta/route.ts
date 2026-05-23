import { NextResponse } from "next/server";
import { loadTargets } from "@/lib/targets";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function GET() {
  const t = await loadTargets();
  return NextResponse.json(t, {
    headers: { "cache-control": "public, s-maxage=300, stale-while-revalidate=86400" },
  });
}
