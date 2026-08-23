export const BRAND = {
  product: "MobiStack",
  organization: "Prabhix Technologies Pvt Ltd",
  tagline: "Building software that simplifies business",
  copyrightYear: 2026,
  publicOrigin: "https://mobistack.prabhixtechnologies.com",
  supportEmail: "support@prabhixtechnologies.com",
} as const;

export function copyrightLine(): string {
  return `© ${BRAND.copyrightYear} ${BRAND.organization}. All rights reserved.`;
}
