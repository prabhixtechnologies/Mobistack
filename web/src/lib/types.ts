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
  /** Present only on older API payloads. Passwords are managed by Identity now. */
  mustChangePassword?: boolean;
  systemAdmin?: boolean;
  emailVerified?: boolean;
  phoneVerified?: boolean;
  paymentRequired?: boolean;
  localActivationAvailable?: boolean;
  catalogOnly?: boolean;
  features?: string[];
  planCode?: string | null;
  planName?: string | null;
  periodEnd?: string | null;
  commonsReviewer?: boolean;
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
  variant?: string;
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

export interface CommonsSearchHit {
  id: string;
  name: string;
  kind: "device" | "component" | string;
  brandId?: string;
  brandName?: string;
  categoryCode?: string;
  variant?: string;
  modelCode?: string;
}

export interface GlobalSearchResponse {
  query: string;
  devices: DeviceSearchHit[];
  parts: PartSearchHit[];
  brands: { id: string; name: string; deviceCount: number }[];
  exactMatch?: PartSearchHit;
  totalResults: number;
  tookMillis: number;
  commonsDevices?: CommonsSearchHit[];
  commonsComponents?: CommonsSearchHit[];
}

export interface DeviceSummary {
  id: string;
  name: string;
  brandId: string;
  brandName: string;
  modelCode?: string;
  variant?: string;
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
  catalogComponentId?: string | null;
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

export interface CategoryOverview {
  id: string;
  code: string;
  name: string;
  icon?: string;
  color?: string;
  sortOrder: number;
  groupCount: number;
}

export interface CompatibilityOverview {
  categories: CategoryOverview[];
  totalGroups: number;
}

export interface GroupDevice {
  deviceModelId: string;
  deviceName: string;
  brandName?: string;
  variant?: string;
  primaryDevice: boolean;
}

export interface CompatibilityGroup {
  id: string;
  code: string;
  name: string;
  categoryId?: string;
  categoryName?: string;
  notes?: string;
  verified: boolean;
  active: boolean;
  devices: GroupDevice[];
  linkedProductCount: number;
  createdAt?: string;
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

/** `GET /api/v1/users` — mirrors `UserDtos.UserResponse`. */
export interface WorkspaceUser {
  id: string;
  fullName: string;
  email: string;
  phone?: string;
  avatarUrl?: string;
  active: boolean;
  lastLoginAt?: string;
  roles: string[];
  permissions: string[];
}

/** `GET /api/v1/roles` — mirrors `UserDtos.RoleResponse`. */
export interface WorkspaceRole {
  id: string;
  code: string;
  name: string;
  description?: string;
  systemRole: boolean;
  seniority: number;
  permissions: string[];
}

/** `GET /api/v1/auth/sessions` — mirrors `DeviceSessionService.SessionCard`. */
export interface DeviceSession {
  id: string;
  deviceId: string;
  userAgent?: string;
  ipAddress?: string;
  createdAt: string;
  expiresAt: string;
  current: boolean;
}

/** `GET /api/v1/system/health` — mirrors `SystemHealthController.SystemStatus`. */
export interface SystemStatus {
  status: string;
  components: { name: string; status: string; detail?: string }[];
  runtime: {
    uptimeSeconds: number;
    cpuUsage: number;
    heapUsedBytes: number;
    heapMaxBytes: number;
    dbPoolActive: number;
    dbPoolMax: number;
    requestCount: number;
    serverErrorCount: number;
    meanRequestMillis: number;
  };
}

export interface ApiError {
  code: string;
  message: string;
  violations?: { field: string; message: string }[];
}

export interface CommonsStats {
  shopCount: number;
  brandCount: number;
  deviceCount: number;
  componentCount: number;
  fitmentCount: number;
}

export interface CommonsBrand {
  id: string;
  name: string;
  logoUrl?: string | null;
}

export interface CommonsDevice {
  id: string;
  brandId: string;
  brandName?: string | null;
  name: string;
  variant?: string | null;
  modelCode?: string | null;
  releaseYear?: number | null;
}

export interface CommonsComponent {
  id: string;
  categoryCode: string;
  name: string;
  description?: string | null;
  attributes?: Record<string, unknown>;
}

export interface CommonsFit {
  fitmentId: string;
  componentId: string;
  componentName?: string | null;
  deviceId: string;
  deviceName?: string | null;
  fit: string;
  confirmations: number;
  disputes: number;
  disputed: boolean;
  verified: boolean;
}

export interface CommonsStanding {
  accepted: number;
  rejected: number;
  trusted: boolean;
  banned: boolean;
  bannedReason?: string | null;
}

export interface CommonsContribution {
  id: string;
  kind: string;
  status: string;
  targetId?: string | null;
  appliedId?: string | null;
  reason?: string | null;
  reviewNote?: string | null;
  summary?: string | null;
  createdAt: string;
  reviewedAt?: string | null;
}

export interface CatalogStockRow {
  variantId: string;
  sku: string;
  name: string;
  available: number;
  componentId?: string | null;
}

export interface CommonsReviewer {
  userId: string;
  email?: string | null;
  fullName?: string | null;
  grantedBy?: string | null;
  grantedAt?: string | null;
  reason?: string | null;
}
