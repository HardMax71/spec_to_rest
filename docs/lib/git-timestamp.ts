import { execSync } from "node:child_process";
import path from "node:path";

const REPO_ROOT = path.resolve(process.cwd(), "..");

export function getGitLastModified(repoRelPath: string): Date | null {
  try {
    const out = execSync(
      `git log -1 --pretty=format:%cI -- ${JSON.stringify(repoRelPath)}`,
      { cwd: REPO_ROOT, stdio: ["ignore", "pipe", "ignore"] },
    )
      .toString()
      .trim();
    if (!out) return null;
    return new Date(out);
  } catch {
    return null;
  }
}
