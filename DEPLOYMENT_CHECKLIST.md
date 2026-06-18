# Freebox — Deployability Checklist
_Updated 2026-06-16 after a fix/implement pass. Status verified against build output, Supabase advisors, migrations, Edge Functions, and code._

## ✅ DONE this pass (autonomous)
- **Package renamed** `com.example.myapplication` → **`com.freebox.app`** (38 files + namespace + applicationId). Builds green.
- **Release signing** — upload keystore generated, `signingConfigs.release` wired from gitignored `keystore.properties`. (Swap in your own key for the real Play upload.)
- **R8 + resource shrinking ON** for release, with kotlinx-serialization/Ktor/Supabase keep rules → **signed AAB builds** (`app/build/outputs/bundle/release/app-release.aab`, ~8 MB).
- **Paywall bypass closed in release** — the dev self-grant is now `BuildConfig.DEBUG`-gated; release builds cannot self-grant (they hold at the paywall until Play Billing exists).
- **Account deletion (Play-required)** — `delete-account` Edge Function (JWT-verified, service-role `deleteUser` → cascades all data) + Settings "Delete Account" danger zone with confirm dialog → signs out.
- **Privacy Policy + Data Safety** content authored (`PRIVACY_POLICY.md`, `PLAY_DATA_SAFETY.md`). _Hosting the URL + submitting the form = your Play Console step._
- **Password reset** — "Forgot password?" → `resetPasswordForEmail`.
- **Stale copy fixed** — Welcome "Log In" now opens real auth (was "coming soon"); email in Settings is the real signed-in email.
- **RLS performance** — all 12 policies wrapped `(select auth.uid())`; **FK indexes** added. Advisors clean (only intentional warnings remain).
- **git initialized** + initial commit (was untracked; it had been lost once).
- **Onboarding step label** corrected.

## ⛔ REMAINING — needs YOUR external accounts/resources (cannot be done from here)
1. **Real Play Billing purchase** — needs a Google Play Console: create the subscription products ($4.99/mo, $29.99/yr), upload to an internal-testing track, then build the Billing client + a purchase-verification Edge Function (Play Developer API service account). Until then, release builds can't unlock (by design); **debug builds unlock via the dev grant for testing**. Also: drop the `dev_grant_entitlement` RPC before public release.
2. **Scraper live data** — needs `scraper/.env` (your `SUPABASE_SERVICE_ROLE_KEY` + a residential `PROXY_URL`), `playwright install chromium`, and one `--log-level DEBUG` run to resolve the 7 `# ⚠️ VERIFY` field mappings. Until then the app serves the 12 seed listings (real DB data).
3. **Scraper hosting** — a always-on/cron host (VPS/container) to run the worker continuously.
4. **Device QA** — ← this is your next step (cloud phone). Everything is built and runnable.
5. **Play Console listing** — store listing, screenshots, content rating (IARC), Data Safety form submission, and hosting the privacy-policy URL.

## 🟡 REMAINING — implementable but deferred (large features / low test value now)
- **GPS distance** (justifies the location permission) — needs `play-services-location`; listing distance is a placeholder (`distanceMiles=0`). Either implement or remove the location permission before submitting.
- **Push notifications** for alerts — needs FCM (Firebase was removed); `POST_NOTIFICATIONS` is declared but nothing fires. Implement or remove the permission.
- **Scanner** (CameraX + AI valuation) and **Map** (Google Maps SDK) are still polished mocks.
- **Coil image loading** — wired nowhere yet; seed listings have no `image_url`, so it only matters once the scraper supplies photos.
- **Trends analytics** — static; needs a sales/sold-price model.
- **Automated tests** — none; add smoke coverage for auth/paywall/repos.

## How to run on the cloud phone (Android Studio Device Streaming)
1. Open the project in Android Studio → reserve a Device Streaming device.
2. Run the **debug** build (the dev paywall unlock works only in debug).
3. **For frictionless signup:** in the Supabase dashboard → Authentication → Email, turn **off "Confirm email"** (otherwise signup says "check your email"). Then: sign up → onboarding (pick interests, enter a ZIP) → paywall → "Start 7-Day Free Trial" (debug-unlocks) → browse the 12 seed listings → tap one → Claim → save to Vault → create an alert → Profile shows the count → Settings → Delete Account works.
