export const BRAND = {
  product: "MobiStack",
  organization: "Prabhix Technologies Pvt Ltd",
  tagline: "See what fits. See stock. Sell.",
  organizationTagline: "Building software that simplifies business",
  copyrightYear: 2026,
  publicOrigin: "https://mobistack.prabhixtechnologies.com",
  supportEmail: "support@prabhixtechnologies.com",
} as const;

export function copyrightLine(): string {
  return `© ${BRAND.copyrightYear} ${BRAND.organization}. All rights reserved.`;
}
