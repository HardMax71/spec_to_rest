import type { DeploymentProfile } from "#profile/types.js";
import { PYTHON_FASTAPI_POSTGRES } from "#profile/python-fastapi.js";

const PROFILES: ReadonlyMap<string, DeploymentProfile> = new Map([
  ["python-fastapi", PYTHON_FASTAPI_POSTGRES],
  ["python-fastapi-postgres", PYTHON_FASTAPI_POSTGRES],
]);

const CANONICAL_NAMES: readonly string[] = ["python-fastapi"];

export function getProfile(name: string): DeploymentProfile {
  const profile = PROFILES.get(name);
  if (!profile) {
    const available = CANONICAL_NAMES.join(", ");
    throw new Error(`Unknown deployment profile '${name}'. Available: ${available}`);
  }
  return profile;
}

export function listProfiles(): readonly string[] {
  return CANONICAL_NAMES;
}
