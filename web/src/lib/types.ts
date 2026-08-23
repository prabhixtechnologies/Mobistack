export type StockStatus = "GREEN" | "ORANGE" | "RED";
export type PricingFlag = "NORMAL" | "WHOLESALE" | "REPAIR" | "VIP" | "CLEARANCE" | "OLD_STOCK" | "CUSTOM";

export type MembershipStatus = "INVITED" | "PENDING" | "ACTIVE" | "SUSPENDED" | "REMOVED" | "REJECTED";

export interface WorkspaceCard {
  id: string;
  name: string;
  city?: string;
  logoUrl?: string;
  joinCode?: string | null;
  role: string;
  status: MembershipStatus;
  memberCount: number;
  productCount: number;
  selected: boolean;
}

export interface MyWorkspacesResponse {
  selectedWorkspaceId?: string | null;
  workspaces: WorkspaceCard[];
}

export interface AuthenticatedUser {
  id: string;
  shopId?: string | null;
  shopName?: string | null;
  workspaceId?: string | null;
  workspaceName?: string | null;
  fullName: string;
  email: string;
  phone?: string;
  roles: string[];
  permissions: string[];
  mustChangePassword: boolean;
  systemAdmin?: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: AuthenticatedUser;
  workspaces?: WorkspaceCard[];
  deviceId?: string;
}

export function selectedWorkspaceId(user: AuthenticatedUser | null | undefined): string | null {
  return user?.workspaceId ?? user?.shopId ?? null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface InventorySnapshot {
  totalProducts: number;
  totalVariants: number;
  stockUnits: number;
  stockValueAtCost: number;
  stockValueAtRetail: number;
  lowStockCount: number;
  outOfStockCount: number;
  openAlertCount: number;
}

export interface StockAlert {
  id: string;
  productVariantId: string;
  variantName?: string;
  productName?: string;
  alertType: string;
  severity: StockStatus;
  status: string;
  observedValue?: number;
  thresholdValue?: number;
  message: string;
  createdAt: string;
}

export interface DashboardResponse {
  sales: { todaySales: number; todayProfit: number; todayTransactions: number };
  repairs: { received: number; diagnosing: number; inRepair: number; ready: number; delivered: number; pending: number };
  inventory: InventorySnapshot;
  alerts: StockAlert[];
}

export interface DeviceSearchHit {
  id: string;
  name: string;
  brandId: string;
  brandName: string;
  modelCode?: string;
  matchedAliases: string[];
  partsInStock: number;
  score: number;
}

export interface PartSearchHit {
  variantId: string;
  productId: string;
  productName: string;
  variantName: string;
  sku: string;
  barcode?: string;
  categoryName?: string;
  grade?: string;
  availableQty: number;
  stockStatus: StockStatus;
  price: number;
}

export interface GlobalSearchResponse {
  query: string;
  devices: DeviceSearchHit[];
  parts: PartSearchHit[];
  brands: { id: string; name: string; deviceCount: number }[];
  exactMatch?: PartSearchHit;
  totalResults: number;
  tookMillis: number;
}

export interface DeviceSummary {
  id: string;
  name: string;
  brandId: string;
  brandName: string;
  modelCode?: string;
  aliases: string[];
}

export interface PartOption {
  variantId: string;
  productId: string;
  productName: string;
  variantName: string;
  sku: string;
  grade?: string;
  quality?: string;
  color?: string;
  onHandQty: number;
  reservedQty: number;
  availableQty: number;
  stockStatus: StockStatus;
  price: number;
  costPrice: number;
  marginAmount: number;
  location?: string;
}

export interface CategoryPartsSummary {
  categoryId: string;
  categoryCode: string;
  categoryName: string;
  icon?: string;
  color?: string;
  variantCount: number;
  totalAvailable: number;
  stockStatus: StockStatus;
  minPrice?: number;
  maxPrice?: number;
  options: PartOption[];
}

export interface DeviceCompatibilityView {
  device: DeviceSummary;
  compatibleModels: DeviceSummary[];
  groups: { id: string; code: string; name: string; categoryName?: string; verified: boolean; deviceCount: number }[];
  categories: CategoryPartsSummary[];
  totalPartsAvailable: number;
  categoriesInStock: number;
  categoriesOutOfStock: number;
  pricingFlag: PricingFlag;
}

export interface ProductVariant {
  id: string;
  productId: string;
  productName: string;
  categoryName?: string;
  variantName: string;
  sku: string;
  barcode?: string;
  grade?: string;
  quality?: string;
  color?: string;
  costPrice: number;
  retailPrice: number;
  wholesalePrice?: number;
  repairPrice?: number;
  minPrice?: number;
  onHandQty: number;
  reservedQty: number;
  availableQty: number;
  reorderLevel: number;
  stockStatus: StockStatus;
  stockValueAtCost: number;
  location?: string;
  lastSoldAt?: string;
  active: boolean;
}

export interface Category {
  id: string;
  code: string;
  name: string;
  icon?: string;
  color?: string;
  sortOrder: number;
  compatibilityRelevant: boolean;
  active: boolean;
}

export interface Brand {
  id: string;
  name: string;
  color?: string;
  sortOrder: number;
  active: boolean;
  deviceCount: number;
}

export interface InventoryTransaction {
  id: string;
  productVariantId: string;
  type: string;
  quantity: number;
  onHandDelta: number;
  balanceAfter: number;
  unitCost?: number;
  reason?: string;
  occurredAt: string;
  createdByName?: string;
}

export interface ApiError {
  code: string;
  message: string;
  violations?: { field: string; message: string }[];
}
