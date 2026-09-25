# New user joins Bihar mobile union

After the account exists, the first MobiStack screen offers both choices. They can create their own shop, or join a shop that already exists. Joining an existing shop is how someone enters a counter that is already in Bihar mobile union, without opening a second one.

Bihar mobile union is one fitment group. A fitment row (this part fits this phone) belongs to that group. Stock, sales, and customers stay on the shop. The person sees that compatibility when the shop they have open is a member of the group. Everyone with an active membership in that shop can read it. Joining does not make them a union admin.

## Order of screens

1. **Welcome** — Create account, or Login.
2. **Identity** — hosted sign-up or sign-in, then back to the app.
3. **Shop start** — two actions: **Create shop** and **Join a shop**.
4. **Create shop** — name and city. No payment. Then **Join the union**.
5. **Join a shop** — the existing shop's code. A recognized code opens payment.
6. **Join the union** — the union code, shown after their own shop exists. A recognized code opens payment.
7. **Payment** — Razorpay, ₹50 once, price `WORKSPACE_JOIN`, only after the code on that screen is accepted.
8. **Waiting** — payment is captured, and the owner or a union admin has not approved yet.
9. **Compatibility** — the union's phones and parts.

| After Identity returns | Next screen |
|---|---|
| No shop yet | **Shop start**. Create shop and Join a shop are both on screen. |
| They chose Join a shop, code not accepted yet | **Join a shop**. |
| They created a shop, and it is not in the union yet | **Join the union**. |
| Code was accepted, payment not captured | Stay on the join screen they came from. Payment opens when they submit again. |
| Paid to enter an existing shop, owner has not approved | **Waiting** for that shop. |
| Paid to put their own shop in the union, admin has not admitted it | **Waiting** for the union. |
| The open shop is a member of Bihar mobile union | **Compatibility**. |
| The open shop is in several groups and none is selected | **Group picker**, then Compatibility. |
| Signed out | **Welcome**. |

The catalog does not open until an active shop in the union is selected. Creating an account does not charge them. Creating a shop does not charge them. Payment is only the step after a valid join code.

## 1. Account

Create account opens Identity with `prompt=create`. The form is name, email, and one password of at least 10 characters. It does not ask for a workspace name. That field would create an OneOps organization, which is neither this shop nor the union.

| What they submit | What happens |
|---|---|
| Email already has an active account | Stay. Say the address is taken. Offer Login. |
| Password shorter than 10 characters | Stay. Keep name and email. |
| They dismiss the hosted page | Welcome. No shop. |
| Identity cannot finish provisioning | Stay. Say nothing was saved and they can try again. The account is withdrawn. |
| Success | Back in the app. **Shop start**. |

Login uses the same return and the table above. A wrong password stays on Identity. Someone who already has an active shop in the union goes to Compatibility and does not see Shop start.

## 2. Shop start

Shown when they have no active shop. Both actions are visible.

- **Create shop** — they are opening their own counter, then they will join the union with the union code.
- **Join a shop** — they are entering a counter that already exists. If that shop is already in Bihar mobile union, this is the whole path into the shared compatibility.

## 3. Join a shop

The code is the join code from that shop's settings. The button stays disabled while the field is empty. Payment does not open until the code belongs to a real shop.

| Code | Next |
|---|---|
| Empty | Stay. |
| No shop has that code | Stay. "No shop with that code." No payment sheet. |
| They already have `ACTIVE` in that shop | Leave this screen. If the shop is in the union, **Compatibility**. If it is not, say so. They are in the shop, and a union admin still has to add the shop. |
| They already have `PENDING` or `INVITED` | **Waiting** for that shop. Do not charge again. |
| `REMOVED` or `REJECTED` | They may ask again. Continue to payment. |
| An unused captured `WORKSPACE_JOIN` payment already exists for this person and this shop | Skip Razorpay. Record `PENDING`. **Waiting**. |
| Code matches | **Payment**. |

| After payment | Next |
|---|---|
| They close the sheet | Back to Join a shop. The code stays. Nothing is captured. |
| Signature does not match | Stay. "Payment signature did not match." |
| Razorpay is not configured in production | Stay. "Razorpay is not configured." |
| Captured | `PENDING` membership as viewer. The ₹50 is consumed. **Waiting** for the shop owner. |

| While waiting on a shop | Next |
|---|---|
| They cancel | Delete the pending row. Put the ₹50 back on this shop so the next request does not charge again. Return to Shop start. |
| Owner or admin approves, and the shop has `MEMBER_ADD` | Membership becomes `ACTIVE`. Select that shop. |
| The shop is in Bihar mobile union | **Compatibility**. Done. |
| The shop is not in the union | They are in the shop. Compatibility for this group stays closed until a union admin adds the shop. |
| Approver rejects | `REJECTED`. Return to Shop start. A later request can pay again. |
| The shop has no `MEMBER_ADD` at approval | Approval fails on the owner's screen. The joiner stays on Waiting. A catalog-only shop cannot take on people. |
| Shop is deactivated | They cannot open it. |

An owner invite is the free version of this door. From Shop start they can paste the token instead of a code. No Razorpay. The token lasts 7 days.

| Token | Next |
|---|---|
| Unknown | Stay. "Invitation is not valid." |
| Already used, or cancelled | Stay. "This invitation has already been used." |
| Past expiry | Mark it expired. Stay. "This invitation has expired." |
| Shop no longer has `MEMBER_ADD` | Stay. "Adding people to this shop needs an active plan." |
| Valid | `ACTIVE` in the invited role. Select the shop. Same landing as an approved join. |

## 4. Create shop

Name is required. City is optional. No Razorpay.

| Result | Next |
|---|---|
| Name blank | Stay on Create shop. Shop start is still one step back. |
| Saved | They are the owner. Membership is `ACTIVE`. The shop gets its own join code for later staff. The app selects this shop. **Join the union**. |

## 5. Join the union

Shown only once their own shop exists and is not in Bihar mobile union yet. The code is the union code, not the shop code from the previous section. The button stays disabled while the field is empty.

| Code | Next |
|---|---|
| Empty | Stay. |
| Not the union's code | Stay. "That code is not for Bihar mobile union." No payment sheet. |
| Shop is already a member | **Compatibility**. |
| A request is already waiting | **Waiting** for the union. Do not charge again. |
| An unused captured payment already exists for this shop and this group | Skip Razorpay. **Waiting**. |
| Code matches | **Payment**, same ₹50 `WORKSPACE_JOIN`. |

| After payment | Next |
|---|---|
| They close the sheet | Back here. The code stays. Nothing is captured. |
| Signature does not match, or Razorpay is not configured | Stay. Same messages as on Join a shop. |
| Captured | **Waiting** for a union admin. |
| They cancel while waiting | Withdraw the request. Put the ₹50 back. Return here. |
| Union admin admits the shop | The shop is a `MEMBER`. Next refresh opens **Compatibility**. |
| Union admin refuses | Return here. A later try can pay again. |
| Union admin removes the shop later | Leave Compatibility and return here. |

A union admin sees the group's code on the members screen and admits a paid request there. Until they admit the shop, compatibility stays closed.

## 6. Compatibility

| What they do | What they see |
|---|---|
| The shop is only in this group | The union's phones and parts. No picker. |
| The shop is in several groups | Picker first. The chosen id is sent as `X-Fitment-Group`. |
| They pick a group the shop is not in | "You are not in that fitment group." Stay on the picker. |
| They search a phone | Parts this group has linked to that phone. |
| They add or edit a fitment | Stored on this group. Other shops in the union see it. Shops outside the union do not. |
| Stock, a sale, or a customer | Stays on this shop. The union does not list it. |

## Payments

| Moment | Asked to pay? |
|---|---|
| Create account | No |
| Shop start | No |
| Create shop | No |
| Join a shop, or Join the union, code empty or wrong | No |
| Either join screen, code matches | Yes. ₹50 once. `WORKSPACE_JOIN`. |
| Owner invite | No |
| Already paid, and they cancelled that request | No. The same ₹50 is reused for that shop. |
| Waiting, or already in the union | No |

## Returning to the app

| Session | Next |
|---|---|
| No Identity session | Welcome. |
| Session, no shop | Shop start. Both actions are there. |
| Session, join of an existing shop still unpaid | Join a shop. |
| Session, own shop exists, union join still unpaid | Join the union. |
| Session, paid, not approved | Waiting. |
| Session, open shop is in the union | Compatibility. |
