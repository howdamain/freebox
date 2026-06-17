# Freebox — Deployability Analysis & Checklist
_Generated 2026-06-16. Every item below was verified against the build config, AndroidManifest, Supabase advisors, migrations, or a code grep this session — not from memory._

## Where it stands (solid foundation)
- App compiles clean → 24 MB **debug** APK. ~30 Kotlin files, Compose UI, all 16 designed screens implemented.
- Backend live (Supabase project `bxonxmthyynwmtayzvge`): 8 tables, RLS on everything, hard paywall enforced at the data layer (`is_entitled()` gates `listings`).
- Wired & building: email auth + session gating, entitlement/paywall gate, live Discover/Map/Details/Vault/Profile/alerts on real data, atomic claim RPC, demand-driven scrape scheduling (ZIP entered in onboarding → `activate_zip`).
- Scraper worker code complete (DIY Playwright + GraphQL interception), compiles.

**Net: it's a working prototype against a real backend. It is NOT yet shippable.** The gaps below are concrete.

---

## P0 — HARD BLOCKERS (cannot publish to Play at all)

1. **Real applicationId.** Currently `com.example.myapplication` (verified `app/build.gradle.kts`). Play rejects `com.example.*`. Rename namespace + applicationId + all package declarations + dir move (e.g. `com.freebox.app`). Touches ~30 files.
2. **Release signing.** No `signingConfig` exists (verified: 0 matches). Need an upload keystore, `signingConfigs.release`, and Play App Signing enrollment. Without it there is no signed artifact.
3. **Signed AAB.** Only a debug APK has ever been built (verified: no `app/build/outputs/bundle/`). Need `./gradlew bundleRelease` producing a signed `.aab` — and it must actually succeed (never attempted).
4. **Kill the paywall bypass.** `dev_grant_entitlement()` lets ANY signed-in user self-grant Pro for free (verified via security advisor: callable by `authenticated`). Must be dropped — but only *after* real billing exists (#5), or the app has no working entitlement path.
5. **Google Play Billing (the biggest missing system).** The hard paywall has no real billing — entitlement is self-granted. Required: Play Billing client + subscription products in Play Console ($4.99/mo, $29.99/yr) + **server-side purchase verification** (Edge Function → Google Play Developer API) that writes `entitlements`. Stripe is prohibited for digital subs. Only fully testable via a Play Console internal track.
6. **Privacy Policy + Play Data Safety form.** Neither exists (verified: only a cosmetic "Privacy Policy" row in Settings, no URL/content). Mandatory for any Play app; doubly so with accounts + a declared location permission. Need a hosted policy URL + completed Data Safety declarations.
7. **In-app account deletion.** Play requires apps with sign-up to offer in-app account deletion + a public deletion URL. Not implemented (no delete-account code). Needs a Settings action + a Supabase user-deletion path.
8. **Run it on a real device.** Zero runtime verification — every flow (auth, paywall, live data, claim, nav, onboarding) is compile-verified only. Local emulator is blocked by disk; use Android Studio Device Streaming or a physical device before ship.

---

## P1 — LAUNCH-CRITICAL (needed for a credible v1)

9. **Enable R8 + serialization keep rules.** Release has `isMinifyEnabled = false` (verified). Ship with shrinking on, and add ProGuard keep rules for kotlinx-serialization / Supabase / Ktor — serializers break under R8 without them.
10. **The scraper has never run → no real inventory.** App shows only 12 seed listings. The worker needs (a) `scraper/.env` with `SUPABASE_SERVICE_ROLE_KEY` + `PROXY_URL` (verified absent), (b) `playwright install chromium`, and (c) one `--log-level DEBUG` tuning pass to resolve the **7 `# ⚠️ VERIFY` items** (FB category slugs, zip→place_id binding, GraphQL JSON paths, Craigslist subdomain map/selectors, OfferUp endpoints). Without this, there is no product.
11. **Scraper hosting.** The worker is local code with no continuous runtime. Needs a scheduled host (VPS/cron/container) holding the service-role key + proxy.
12. **Stale/contradictory copy.** Welcome still says "Accounts are coming soon" though auth is live; Settings says "Account editing is coming soon" (both verified). Misleading — fix before launch.
13. **Auth completeness.** Email confirmation is ON (signup → "check your email") with no password reset, no resend, no unconfirmed-login messaging. Decide confirmation policy + add password reset.
14. **Real launcher icon.** Currently a generated placeholder green diamond. Needs a real brand icon set.
15. **Decide on declared-but-unused permissions.** `ACCESS_FINE/COARSE_LOCATION` + `POST_NOTIFICATIONS` are declared (verified manifest) but GPS distance is a placeholder and no notification ever fires (no FCM — verified). Play will challenge unused permissions. Either implement (GPS + alert push) or remove until you do.

---

## P2 — QUALITY / SCALE / POLISH

16. **RLS perf.** 13 `auth_rls_initplan` warnings (verified): wrap `auth.uid()` as `(select auth.uid())` in every policy — one migration, big win at scale.
17. **Add FK indexes** on `listings.claimed_by` and `vault_items.listing_id` (verified advisor).
18. **Automated tests: zero exist** (verified — no test files). Add at least smoke coverage for auth, paywall gate, and repositories.
19. **Static/placeholder data:** Trends analytics is hardcoded (needs a sales/profit model); listing images not loaded (no Coil; `listings.image_url` unused); Settings email hardcoded.
20. **Onboarding progress bar** reads "Step 2 of 4" but onboarding is now 5 steps (cosmetic).
21. **Supabase plan.** Free tier pauses on inactivity / has limits — upgrade before public launch.
22. **No version control.** Project is NOT a git repo (verified). Given it was already lost once, `git init` + a remote is strongly advised.

---

## PRODUCT GAPS (intentional mocks, not yet real features)
- **Scanner** — simulated viewfinder; no CameraX, no AI valuation (no camera deps — verified).
- **Map** — stylized Canvas mock; no Google Maps SDK (verified), distance is a placeholder.
- **Watchlist add** works from Details; Map-sheet bookmark + Scanner "Save to Vault" are still local-only.

## LEGAL / POLICY (judgment calls — user has accepted ToS risk)
- Scraping FB Marketplace / OfferUp / Craigslist violates their ToS; **displaying/reselling** their listing data adds takedown + legal exposure beyond just being blocked.
- Hard paywall + scraped third-party content + "profit" framing = elevated Play review scrutiny. The store listing must be transparent about what the app does and where data comes from.

---

## Recommended immediate sequence
1. Rename applicationId (#1) → unblocks everything downstream.
2. Stand up signing + a green `bundleRelease` with R8 + keep rules (#2, #3, #9).
3. Implement Play Billing + server verification, then drop `dev_grant_entitlement` (#5, #4).
4. Privacy policy + Data Safety + account deletion (#6, #7).
5. One device QA pass (#8) + the scraper's first live tuning run behind a proxy (#10).
