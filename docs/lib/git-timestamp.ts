import { execSync } from "node:child_process";
import { statSync } from "node:fs";
import path from "node:path";

const REPO_ROOT = path.resolve(process.cwd(), "..");

function gitCommittedDate(repoRelPath: string): Date | null {
  try {
    const out = execSync(
      `git log -1 --follow --pretty=format:%cI -- ${JSON.stringify(repoRelPath)}`,
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

function fileMtime(repoRelPath: string): Date | null {
  try {
    return statSync(path.resolve(REPO_ROOT, repoRelPath)).mtime;
  } catch {
    return null;
  }
}

export function getGitLastModified(repoRelPath: string): Date | null {
  const committed = gitCommittedDate(repoRelPath);
  const mtime = fileMtime(repoRelPath);
  if (committed && mtime) return mtime > committed ? mtime : committed;
  return committed ?? mtime;
}
