export interface GroupDevice {
  deviceModelId: string;
  deviceName: string;
  brandName?: string;
  variant?: string;
  primaryDevice: boolean;
}

export interface CompatibilityGroup {
  id: string;
  name: string;
  categoryId?: string;
  categoryName?: string;
  verified: boolean;
  devices: GroupDevice[];
  linkedProductCount: number;
}

export interface CategoryOverview {
  id: string;
  code: string;
  name: string;
  color?: string;
  groupCount: number;
}

export interface CompatibilityOverview {
  categories: CategoryOverview[];
  totalGroups: number;
}

export function deviceLabel(device: GroupDevice): string {
  return [device.brandName, device.deviceName, device.variant].filter(Boolean).join(" ");
}

export function groupLine(devices: GroupDevice[] | undefined): string {
  return (devices ?? []).map(deviceLabel).filter(Boolean).join(" = ");
}

export function splitEqualsLine(text: string): string[] {
  return text
    .split("=")
    .map((part) => part.trim())
    .filter(Boolean);
}
