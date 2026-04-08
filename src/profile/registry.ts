import type { DeploymentProfile } from "#profile/types.js";
import { PYTHON_FASTAPI_POSTGRES } from "#profile/python-fastapi.js";

const PROFILES: ReadonlyMap<string, DeploymentProfile> = new Map([
  ["python-fastapi-postgres", PYTHON_FASTAPI_POSTGRES],
]);

export function getProfile(name: string): DeploymentProfile {
  const profile = PROFILES.get(name);
  if (!profile) {
    const available = [...PROFILES.keys()].join(", ");
    throw new Error(`Unknown deployment profile '${name}'. Available: ${available}`);
  }
  return profile;
}

export function listProfiles(): readonly string[] {
  return [...PROFILES.keys()];
}
