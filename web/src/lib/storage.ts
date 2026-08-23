const NS = "mobistack";
const LEGACY = "fixflow";

export function storeGet(name: string): string | null {
  return localStorage.getItem(`${NS}.${name}`) ?? localStorage.getItem(`${LEGACY}.${name}`);
}

export function storeSet(name: string, value: string): void {
  localStorage.setItem(`${NS}.${name}`, value);
  localStorage.removeItem(`${LEGACY}.${name}`);
}

export function storeRemove(...names: string[]): void {
  for (const name of names) {
    localStorage.removeItem(`${NS}.${name}`);
    localStorage.removeItem(`${LEGACY}.${name}`);
  }
}
