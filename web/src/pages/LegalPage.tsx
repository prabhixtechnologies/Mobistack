import { Link } from "react-router-dom";
import { BRAND, copyrightLine } from "../lib/brand";
import { ThemeToggle } from "../ui/ThemeToggle";

type Kind = "privacy" | "terms" | "refunds";

const TITLES: Record<Kind, string> = {
  privacy: "Privacy Policy",
  terms: "Terms of Use",
  refunds: "Refunds and cancellation",
};

export function LegalPage({ kind }: { kind: Kind }) {
  return (
    <div className="legal-screen">
      <header className="legal-top">
        <Link to="/login" className="legal-brand">
          {BRAND.product}
        </Link>
        <ThemeToggle compact />
      </header>
      <article className="legal-doc">
        <p className="page-kicker">{BRAND.organization}</p>
        <h1>{TITLES[kind]}</h1>
        <p className="muted">
          Last updated 23 August 2026. This is a product notice for shops using {BRAND.product} in India.
          It is not a substitute for legal advice.
        </p>
        {kind === "privacy" && <Privacy />}
        {kind === "terms" && <Terms />}
        {kind === "refunds" && <Refunds />}
        <p className="muted">{copyrightLine()}</p>
        <p className="legal-nav">
          <Link to="/privacy">Privacy</Link>
          <Link to="/terms">Terms</Link>
          <Link to="/refunds">Refunds</Link>
          <Link to="/login">Sign in</Link>
        </p>
      </article>
    </div>
  );
}

function Privacy() {
  return (
    <>
      <p>
        {BRAND.product} is operated by {BRAND.organization} (“we”). We process shop, staff, and customer
        records so a repair shop can sell parts, take in repairs, and bill for the subscription.
      </p>
      <h2>What we collect</h2>
      <ul>
        <li>Account details: name, email, phone, password hash, and device identifiers.</li>
        <li>Workspace data: shop profile, stock, sales, repairs, customers, and members you enter.</li>
        <li>Billing data: Razorpay order and payment identifiers, amounts, and plan period.</li>
        <li>Security logs: sign-in attempts, audit events, and support messages.</li>
      </ul>
      <h2>Why we use it</h2>
      <p>
        We use this information to run the shop console, send OTP and billing notices, prevent abuse,
        and meet tax or legal requests. We do not sell personal data.
      </p>
      <h2>Legal basis</h2>
      <p>
        Processing is for the contract to provide {BRAND.product}, our legitimate interest in securing
        the service, and consent where you opt into SMS or WhatsApp codes. Indian shops should treat
        customer phone numbers as personal data under the Digital Personal Data Protection Act, 2023.
      </p>
      <h2>Sharing</h2>
      <p>
        Payment is processed by Razorpay. Email uses your configured SMTP provider. SMS and WhatsApp
        use Twilio when you enable them. Hosting may use our cloud and container vendors. They only
        receive what is needed to deliver that function.
      </p>
      <h2>Retention</h2>
      <p>
        Account and shop records stay until the workspace is closed or you ask us to delete them,
        except where law requires a longer hold (for example invoices). OTP secrets expire in minutes
        and are not kept in the shop inbox.
      </p>
      <h2>Your rights</h2>
      <p>
        You can access or correct shop data from Settings, and ask for export or deletion at{" "}
        <a href={`mailto:${BRAND.supportEmail}`}>{BRAND.supportEmail}</a>. Staff accounts can be
        removed by the workspace owner.
      </p>
    </>
  );
}

function Terms() {
  return (
    <>
      <p>
        By creating an account or opening a shop you agree to use {BRAND.product} only for lawful
        mobile-repair operations. The workspace owner is responsible for staff access and for data
        entered into the shop.
      </p>
      <h2>The service</h2>
      <p>
        {BRAND.product} is a hosted shop console. We may improve features, fix defects, and take a
        shop offline if a subscription lapses or if we detect abuse. Offline mobile use is a cache
        of data you already loaded; it is not a second system of record.
      </p>
      <h2>Accounts and payment</h2>
      <p>
        A new shop is created first so Razorpay can attach the payment to that workspace. Sales,
        repairs, stock movements, and member invites stay locked until activation or a monthly plan
        is paid. Plans renew every 31 days from the last captured payment. You must keep payment
        details accurate.
      </p>
      <h2>Acceptable use</h2>
      <p>
        Do not attack the service, share login secrets, upload malware, or use the product to process
        unlawful goods. We may suspend an account that breaks these rules.
      </p>
      <h2>Liability</h2>
      <p>
        The software is provided as a business tool. We are not liable for lost stock counts, missed
        repairs, or gateway downtime beyond a refund of the unused paid period, to the extent Indian
        law allows. GST invoices, if any, follow the shop’s own tax registration.
      </p>
    </>
  );
}

function Refunds() {
  return (
    <>
      <p>
        Activation and monthly {BRAND.product} fees are charged through Razorpay. A successful
        payment unlocks the shop for 31 days.
      </p>
      <h2>When we refund</h2>
      <ul>
        <li>Duplicate capture of the same order, after we confirm it on the gateway.</li>
        <li>A charge made in error by us, or a payment that never unlocked the shop.</li>
        <li>A request sent within 7 days of first activation if the shop has not recorded sales.</li>
      </ul>
      <h2>When we do not refund</h2>
      <p>
        Partial months after the shop has been used, gateway fees already settled, and unused days
        after you cancel mid-cycle are not refunded unless required by law.
      </p>
      <h2>How to ask</h2>
      <p>
        Write to <a href={`mailto:${BRAND.supportEmail}`}>{BRAND.supportEmail}</a> with the Razorpay
        payment id from Billing. Approved refunds return to the original payment method on Razorpay’s
        timeline, usually 5–7 working days.
      </p>
    </>
  );
}
