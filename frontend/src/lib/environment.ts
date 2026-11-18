export const currentEnvironment = import.meta.env.VITE_APPOINT_ENVIRONMENT;

export function environmentStorageKey(key: string): string {
  const namespace = currentEnvironment
    ? `appoint.${currentEnvironment}`
    : "appoint";
  return `${namespace}.${key}.v1`;
}
