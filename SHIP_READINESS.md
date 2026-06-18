# Freebox — Ship-Readiness Checklist (Forensic Audit)

_Generated 2026-06-18 from an 18-agent forensic sweep (8 dimension reviewers → adversarial verification of every blocker-class finding → completeness critic), with key items re-verified by hand against the live DB (ref `bxonxmthyynwmtayzvge`) and repo. Every item is grounded in a file:line, command output, or query result._

**Bottom line:** the *build and security foundation is solid* (signed AAB, RLS hard paywall enforced at the data layer, no committed secrets). What blocks shipping is **(1) there is no way to actually charge money, (2) the app has never run on a device, and (3) the data pipeline only runs on your Mac.** Until billing exists, this is a paid app that can't take payment.

---

## ✅ Resolved 2026-06-18 (this session)

- **Server-side purchase verification** — built + deployed the `verify-purchase` Supabase Edge Function (validates a Play purchase token via the Google Play Developer API v2, writes the authoritative `entitlements` row, fails *closed* with 503 until configured). _Residual human step:_ set `GOOGLE_PLAY_SA_JSON` + `GOOGLE_PLAY_PACKAGE_NAME` secrets once Play Console exists. **(Still need the client-side Play Billing purchase flow — that blocker remains below.)**
- **Listing expiry/lifecycle** — added `listings.last_seen_at` (refreshed on every upsert) + `supabase_io.expire_stale_listings()`; the worker marks `available` listings unseen for `LISTING_EXPIRY_DAYS` (3) as `expired` each pass. Verified live.
- **Scraper hosting/scheduling** — added `.github/workflows/scraper.yml` (free GitHub Actions cron, every 30 min) + `scraper/Dockerfile` for container hosts. Made the browser launch lazy (Craigslist/OfferUp are httpx → no Chromium), so CI runs are light. _Residual:_ add `SUPABASE_*`/`PERPLEXITY_API_KEY` repo secrets + commit.
- **Dependency version skew** — bumped `core-ktx 1.10.1→1.15.0`, `lifecycle 2.6.1→2.8.7`, `activity-compose 1.8.0→1.9.3`; rebuilt green. The binary-incompat crash risk is closed (final confirmation still wants a device pass).
- **Privacy policy + data-deletion pages** — authored public-ready `web/privacy.html` + `web/delete-account.html`, a GitHub Pages deploy workflow, and wired the in-app Settings → Privacy Policy link (`strings.xml` URLs) + removed the dead "Location Services" row. _Residual:_ enable GitHub Pages (or any static host) and set the live URLs in `strings.xml` + the Play listing.

_Remaining blockers below: client-side Play Billing, on-device QA. The rest of the checklist (🟡/🟢) is unchanged._

---

## 🔴 Ship blockers — cannot ship until resolved

- [ ] **No Google Play Billing integration** _(large)_ — `app/build.gradle.kts` deps (L75–103) have no `billing-ktx`/`play-services`; no purchase flow anywhere. A hard-paywall paid app literally cannot collect revenue. Integrate Play Billing, register the subscription products in Play Console.
- [ ] **No server-side purchase verification** _(large)_ — `list_edge_functions` returns only `delete-account`; the `entitlements.play_purchase_token` column is never written by any verified path. Entitlement must be granted by an Edge Function that validates the purchase token against the Google Play Developer API; the client must stay advisory-only. (Pairs with billing above.)
- [ ] **Privacy policy not publicly hosted** _(small)_ — only `PRIVACY_POLICY.md` exists in-repo (its own line 5 says "host this at a public URL"). Play requires a public URL **and** an in-app link. Host it + link it in Settings.
- [ ] **Web data-deletion route doesn't exist** _(small)_ — Play mandates a publicly reachable account/data-deletion page in addition to in-app deletion. None is hosted.
- [ ] **Scraper has no hosting or scheduling** _(medium)_ — no Dockerfile/Procfile/cron/cloud manifest anywhere; the worker only runs manually via `python -m freebox_scraper.worker` on this Mac. In production the feed never refreshes. Deploy it (cron/container/cloud function) with the service-role secret.
- [ ] **No listing expiry/lifecycle** _(medium)_ — adapters only ever write `status='available'` (`offerup.py:136`, `craigslist.py:435`); nothing marks claimed/gone/stale listings. The feed fills once and then shows dead listings forever. Add an expiry pass (mark `gone`/`expired` for listings not seen in N crawls or returning 404/410).
- [ ] **App has never run on a real device + zero automated tests** _(medium)_ — `DEPLOYMENT_CHECKLIST.md:21,30` ("Device QA ← your next step"; "Automated tests — none"); no `test/`/`androidTest/` sources exist. Every "no crash" verdict in this audit is **static-only**. Do a full device pass (Android Studio Device Streaming) through onboarding → paywall → feed → detail → claim → vault before submitting.
- [ ] **Dependency version skew — crash-on-launch risk** _(small)_ — `coreKtx 1.10.1`, `lifecycle 2.6.1`, `activity-compose 1.8.0` (all ~2023) against Compose BOM `2026.02.01`. `viewModel()` is used throughout; an old lifecycle artifact against a 2026 Compose runtime is a classic `NoSuchMethodError` at first composition — and it compiles fine, so only device QA would catch it. Bump these AndroidX libs to versions matching the BOM era and re-verify.

> **Verified RESOLVED (was flagged, now clean):** the `dev_grant_entitlement` "any authed user self-grants Pro" finding was a **false positive** from reviewers reading a stale migration. Re-confirmed this session: the function does **not** exist in the live DB, and there are **zero** client references (`grep devGrant|dev_grant` → none). The hard paywall is intact at the data layer (`listings` SELECT gated by `is_entitled()`).

---

## 🟡 Should-fix before launch

**Play policy / store listing**
- [ ] **Remove unused `ACCESS_FINE/COARSE_LOCATION`** _(medium)_ — `AndroidManifest.xml:6–7` + requested at runtime (`FreeboxApp.kt:211`, `PermissionScreen.kt:44`), but GPS/distance was removed. Play rejects location requests with no backing feature. Remove the perms + the runtime prompt (or implement the feature).
- [ ] **Remove `POST_NOTIFICATIONS`** _(small)_ — declared (`AndroidManifest.xml:8`) and requested (`FreeboxApp.kt:244`) but nothing ever fires (no FCM). Drop it until alerts actually notify.
- [ ] **Disclose the hard paywall + scraped-content model in store metadata** _(medium)_ — no free functionality raises review scrutiny; the app reposts third-party Craigslist/OfferUp listings (photos, deep links) behind a paywall → ToS/IP/content risk. Disclose clearly; be ready to justify in review.
- [ ] **Generate a real upload key** _(small)_ — `keystore.properties` is a documented throwaway (`storePassword=freebox-dev-store`, CN=Freebox placeholder). The first upload's signing identity is locked in for the app's life — do this before the first Play upload (or enroll in Play App Signing).

**Fabricated/placeholder data shown to users (credibility)**
- [ ] **Trends tab shows fabricated analytics as real** _(small)_ — no backend, no disclosure. Gate it, label it as sample, or build it.
- [ ] **Profile shows hardcoded "Alex Hunter" / $14,250 / 128 saves** _(small)_ — wire to the real profile/stats or remove the fake numbers.
- [ ] **Scanner shows a fabricated valuation** ("Mid-Century Chair / $450") _(small)_ — it's also unreachable from the UI; remove or gate it cleanly.

**Auth / account**
- [ ] **Password-reset & email-confirmation links dead-end in a browser** _(medium)_ — no deep link back into the app. Add an App Link / redirect so users return to a signed-in state.
- [ ] **Account deletion 500s after a user claims a listing** _(small)_ — `listings.claimed_by` FK is `ON DELETE NO ACTION`. Change to `SET NULL` (or clean up claims in the delete function).
- [ ] **Entitlement expiry inconsistent (client vs RLS)** _(small)_ — expired Pro still reads as entitled client-side. Align the client check with the server `is_entitled()` semantics.
- [ ] **Enable leaked-password protection** _(small)_ — Supabase Auth HaveIBeenPwned check is off (security advisor). One dashboard toggle.

**Crash hardening (found by completeness critic — all small, all real)**
- [ ] **DataStore has no `IOException` handler** _(small)_ — `UserPreferences.kt` reads `dataStore.data.map{}` with no `.catch{}`; a corrupt/first-run read crashes or hangs the splash. Add the standard `.catch { emit(emptyPreferences()) }`.
- [ ] **No guard before `createSupabaseClient` on empty BuildConfig** _(small)_ — `build.gradle.kts:50–51` falls back to `""`; a clean checkout / CI build crashes on first auth call. Add a `require(url.isNotBlank())` with a clear message.
- [ ] **`allowBackup=true` ships the Supabase session token to cloud backup** _(small)_ — set `allowBackup=false` or exclude the auth store via `data_extraction_rules.xml`.
- [ ] **Discover has no loading/error/retry UI** _(small)_ — the ViewModel exposes both but the screen ignores them, so a transient network error looks like "no results."

**Backend / scraper ops**
- [ ] **OfferUp 403s datacenter IPs** _(medium)_ — works from your residential IP, but once hosted set a residential `PROXY_URL` or the `APIFY_TOKEN` fallback (already wired).
- [ ] **No monitoring/alerting on crawl failures** _(small)_ — feed can silently die. Add a heartbeat/health check.
- [ ] **Zero migration files in the repo** _(small)_ — schema/RLS/functions live only in the remote DB (applied via MCP). No reproducibility/rollback. Export them to `supabase/migrations/`.
- [ ] **Rotate the Perplexity API key** _(small)_ — it was pasted in chat; in gitignored `.env` but should be rotated.
- [ ] **Tighten `entitlements` table grants** _(small)_ — direct INSERT/UPDATE granted to anon/authenticated; should be service-role-only (defense-in-depth; RLS currently holds the line).

**Repo hygiene**
- [ ] **`app/build/` is committed (1,960 files incl. AAB/dex)** _(small)_ — add to `.gitignore` and `git rm -r --cached app/build/`. Bloats the repo and churns every build.

---

## 🟢 Nice-to-have

- [ ] `settings.gradle.kts` still `rootProject.name = "My Application"` + `Theme.MyApplication` (cosmetic; user-facing label is correctly "Freebox").
- [ ] Dead code: orphaned `MapViewModel`, dead "unlocked" branch in `ProScreen`, `.env.example` JWT-looking prefix.
- [ ] Unused indexes on `listings`/`alerts`/`claims`/`crawl_targets` (perf advisor INFO).
- [ ] Verify AGP 9.2.1 / Gradle 9.4.1 + `compileSdk { release(36){ minorApiLevel=1 } }` is accepted by Play upload tooling (bleeding-edge toolchain).

---

## ✅ Verified solid (already ship-grade)

- Signed **release AAB builds** (8.68 MB) with R8 minify + resource shrink; kotlinx-serialization/Supabase keep rules correct (serializers mapped to themselves in `mapping.txt`).
- Package fully off `com.example` (`com.freebox.app`); `versionCode=1`/`versionName=1.0`; valid PKCS12 keystore.
- **No live secrets committed**; only the client-safe anon key reaches the app via `BuildConfig`; service-role key is NOT in the app; `.env`/`keystore.properties`/`local.properties` gitignored & untracked.
- **RLS on for all tables; hard paywall enforced at the data layer** — `listings` SELECT gated by `is_entitled()`; vault/alerts/claims/profiles owner-only.
- **`dev_grant_entitlement` paywall bypass: confirmed removed** (DB + client).
- Secure manifest defaults (no cleartext, not debuggable); HTTPS-only Supabase.
- Account-deletion Edge Function exists; auth is session-gated; image coverage 93% data / 100% visual via Coil + placeholder.

---

### The realistic critical path to a first release
1. **Play Billing + server-side verification** (the only way to charge) → 2. **Device QA pass** (catches the version-skew/DataStore/offline crashes) → 3. **Host privacy policy + data-deletion page**, strip unused permissions → 4. **Deploy + monitor the scraper, add listing expiry** so the feed stays live.
