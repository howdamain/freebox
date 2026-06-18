# Google Play — Data Safety form reference (Freebox)

Use this to fill the Play Console **Data safety** section. Reflects what the app actually does as of 2026-06-16.

## Data collected & shared
| Data type | Collected | Shared | Purpose | Optional? |
|---|---|---|---|---|
| Email address | Yes | No | Account management / authentication | Required |
| Approximate location (ZIP, user-entered) | Yes | No | App functionality (area listings) | Required |
| App activity (saved items, alerts, claims) | Yes | No | App functionality | Required |
| Purchase history | Yes (via Play Billing) | No | Subscription entitlement | Required |

- **No precise/background GPS location** is collected (ZIP is user-entered). _If GPS distance is implemented later, update this._
- **No data sold** or shared with third parties for advertising.

## Security practices
- **Encrypted in transit:** Yes (TLS/HTTPS to Supabase).
- **Data deletion:** Yes — users can request deletion **in-app** (Settings → Delete account) which removes all their data; also via the deletion URL.
- **Account-deletion URL** (Play requires a public one): publish a page describing the in-app path + an email request route, e.g. linking to PRIVACY_POLICY.md's deletion section.

## Permissions to justify in the listing
- `INTERNET` — backend communication.
- `ACCESS_FINE/COARSE_LOCATION` — **currently declared but GPS distance is not yet used.** Either implement GPS distance before submitting, or remove these permissions to avoid a policy rejection. (See DEPLOYMENT_CHECKLIST.md #15.)
- `POST_NOTIFICATIONS` — for profit-alert notifications; **only declare once notifications actually fire** (push isn't wired yet).

## Content rating & listing notes
- Complete the IARC content-rating questionnaire (utility app, no mature content).
- Listing must transparently describe that listings are aggregated from public third-party marketplaces.
