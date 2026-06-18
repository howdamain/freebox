# Freebox — 30-45 Day Influencer Blitz (Reworked, Honesty-Checked)

_Generated 2026-06-16 via multi-agent workflow (5 designs → 3 adversarial critics → synthesis), grounded against the live repo + Supabase `bxonxmthyynwmtayzvge`. Verified independently: price strings in ProScreen.kt (.99/.99, default Yearly, 7-day trial); live DB = 12 listings / 9 cities / $2,575 total est_profit / $500 max; scraper/.env absent; promo/referral absent from app/src. Every conversion rate is a labeled directional ASSUMPTION — 0 users, nothing measured._

---

# Freebox — The Honest Blitz: a monthly-led, creator-armed 30-45 day push (with the odds told straight)

> **Grounding note.** Every product-state fact below was verified this session against the actual repo and the live Supabase backend (project `bxonxmthyynwmtayzvge`), not assumed. Specifically: shipped price is **$4.99/mo + $29.99/yr, default "Yearly", 7-Day trial on a shared CTA** (`ProScreen.kt` L49/172/180/260); live DB has **12 listings across 9 cities, max 2 per city, 0 images, total est_profit across the WHOLE database = $2,575** (densest metro = Portland, $310); **0 users, 0 entitlements**; the `listings` SELECT policy is gated by `is_entitled()` (hard paywall is real); the personalized-proof paywall string and any referral/promo code are **absent from `app/src`** (both unbuilt); `scraper/.env` is **absent** (scraper never run); and `valuation.py` is a **keyword/regex heuristic** (hardcoded category baselines × keyword multipliers, ×8 cap, no sold-price comp — the file's own comment says "adjust as real transaction data accumulates"). Every conversion rate, view count, and yield in this plan is a **labeled directional ASSUMPTION** — the app has 0 users, so nothing here is measured. Swap in real numbers the moment week-1 data exists.

---

## 1. The reframe

The right application of influencer marketing here is **not "pay creators to advertise an app."** It's: **turn the app's own output into the creator's content, and the creator's content into the app's proof.** Freebox's output — "free [item] worth $X, 8 minutes from you" — is the *exact* video a reseller creator already makes. So you don't beg for promo; you hand a flipping creator **(a)** pre-scouted real local inventory (you run the scraper on *their* ZIP and give them 5-10 genuine free finds), **(b)** a video that films itself, and **(c)** a recurring commission. The single highest-leverage idea is this value-exchange — it is the one thing that lifts this from a pure lottery toward a loaded one, because it makes recruiting *cheap and credible* and makes each post a self-demonstrating product demo to the most skeptical audience on the internet.

But — and this is the part four of the five candidate designs glossed — **that demo only works if the number on screen is true and the radar isn't empty.** The product as it sits today cannot collect a dollar (no Play Billing live), shows real inventory in only 9 cities at max 2 listings each, and prices its "profit" off a regex with no market comp. Influencer marketing pointed at *that* doesn't fail quietly; it arms r/Flipping — the most expert-skeptical audience alive — with footage of your app being wrong, in their own city, behind a $5 charge. **The reframe's corollary: the first job is not marketing, it's making the product *true and chargeable*. Outreach starts after that, or not at all.**

---

## 2. Monetization & the number

**Lead with monthly. It is the MRR engine.** Each monthly sub = $10 MRR; each annual = only $3.33 normalized. You're judged on MRR, and your audience is broke + scam-skeptical, so monthly ("less than one flip, cancel anytime") is both the MRR-dense choice *and* the lower-friction one. Annual ($40/yr) with the **3-day trial (annual only)** is the secondary "save 67%" upsell and the sole risk-reversal — keep the trial, but don't default to it.

**FIRST, a non-negotiable code fix (verified bug):** the app ships **$4.99/$29.99, default Yearly, 7-day shared-CTA trial**. Every MRR figure below assumes you change the price strings to **$9.99/mo + $39.99/yr**, **pre-select Monthly** (flip `selectedPlan` off `"Yearly"`), and move the trial to **3-day, annual-only**, and create the matching Play Console products. **If you ship at $4.99, halve every MRR number here** — 400+150 subs yields only ~$2,371 MRR, missing the target ~2x. This is the cheapest single point of leverage in the entire plan.

**The mix and the counts (assumption: ~73% monthly / ~27% annual of payers):**

| Plan | Subs | MRR contribution | Cash collected in window |
|---|---|---|---|
| Monthly $9.99 | **~400** | $4,000 | ~$4,000 (first payments) |
| Annual $39.99 | **~150** | ~$500 (normalized) | ~$6,000 (upfront) |
| **Total** | **~550** | **~$4,500 MRR** | **~$10,000 collected** |

The two targets reconcile cleanly: monthly supplies the MRR, annual supplies the cash bump. **Lower bound to clear $3k MRR:** ~270 monthly + ~110 annual ≈ $3,070 MRR / ~$7,100 cash. **The engineering target is ~300-400 active monthly subs (the MRR-defining number) plus ~110-150 annual, monthly-led.**

> Honesty footnote: that $4,500 counts $500 of annual-normalized MRR. Pure recurring-monthly-billing MRR is ~$4,000 from the 400 monthly subs; annual is cash. State which definition you're reporting.

---

## 3. The reverse-engineered funnel (every rate a labeled directional assumption)

Target → ~550 net paying subs (~400 monthly + ~150 annual) → ~$4.5k MRR / ~$10k cash.

**Step 1 — Net subs → gross conversions** (assume ~15% early monthly churn, ~35% annual trial→cancel):
- Monthly gross ≈ 400 / 0.85 ≈ **470 subscribes**
- Annual gross ≈ 150 / 0.65 ≈ **230 trial-starts**
- **≈ 700 paywall conversions / trial-starts needed.**

**Step 2 — Installs → paywall conversion.** Hard, consumable, *paid* paywall to a scam-skeptical audience is the worst-case conversion environment, so I use a **conservative 4%** (assumption; defensible band 2-6%, NOT the 8-12% two designs used to make their math look easy). This is the single most fragile number.
- Installs ≈ 700 / 0.04 ≈ **~17,500 installs.** (At 6% → ~11,700; at 2% → ~35,000.)

**Step 3 — Views → installs.** Niche-matched flipper content (the video IS the demo) but a *paid Android app via link-in-bio → Play Store redirect* suppresses CTR. Assume **0.7%** blended (band 0.3-1.2%).
- Views ≈ 17,500 / 0.007 ≈ **~2.5M qualified views** across the whole window. (At 1.2% → ~1.46M; at 0.3% → ~5.8M.)

**Step 4 — Creators → views (where the power-law lives).** Don't assume everyone breaks out. Assume a mix: most micro/mid posts land 15k-60k views; ~1 in 12-20 posts breaks to 100k-1M+. The honest crux: **~2.5M views requires either the base case landing near the top of its range OR a handful (~5-10) of genuine FYP breakouts** — and breakout timing is not controllable, only loadable.

**Step 5 — Creators to recruit (with realistic SOLO throughput).** This is where I correct Designs 1 & 3: a solo founder cannot personalize 1,200 DMs. The personalized inventory hook (run scraper on their ZIP → 5-10 finds → custom pitch) is **15-45 min each**; solo ceiling is **~15-25 real pitches/day**. So the recruit target is **Design 2's count, not Design 1's**:
- **Recruit ~40-50 creators** (DM ~120-150 over 2 weeks at a sustainable pace; assume ~30% reply, ~50% agree, ~60% actually post → ~10% DM→post, plus the higher-touch personalization lifts reply rate).
- **Net ~15-25 reliable posters**, tiered: ~15-18 micro (10k-80k, flipping/reseller niche), ~3-5 mid (100k-500k) as credibility anchors and breakout shots. Avoid generic "make money online" mega-influencers — most scam-fatigued, lowest-intent.
- **Plus the founder account** posting **1-2 real flips/day** (sustainable — NOT the 3-5/day that guarantees a week-3 collapse).

**Chain summary (all assumptions):** ~120-150 DMs → ~15-25 posters + founder daily → ~2.5M views (needs base-case-high OR a few breakouts) → ~17,500 installs (at 4% pay) → ~700 conversions → ~550 net subs → ~$4.5k MRR / ~$10k cash. The chain multiplies four fragile rates (DM→post, post→views, view→install, install→pay); a 2× miss on any two roughly halves the result. **That fragility, plus the unbuilt paywall asset and 9-city supply desert, is the honest core of the odds in §6.**

---

## 4. The 30-45 day blitz, week by week

### Week 0 — Make it TRUE and CHARGEABLE. No creator outreach yet. (Realistically a 2-4 week solo backend sprint — be honest that the marketing clock starts *after* this.)
- [ ] **Play Billing live**: subscription products in Play Console + a Billing client + a **purchase-verification Edge Function** (Play Developer API receipt validation). Drop the dev `dev_grant_entitlement` grant. *No billing = $0 collected, full stop.*
- [ ] **Price fix** (§2): $9.99/$39.99, pre-select Monthly, 3-day annual-only trial.
- [ ] **Run the scraper for real**: fill `scraper/.env` (service-role key + residential proxy), `playwright install chromium`, one debug run, resolve the `⚠️ VERIFY` field mappings. **Validate dense, accurate inventory in exactly 2-3 launch metros — not nationwide.** Pick from where supply is provable (today only 9 cities exist, max 2 listings each — you must actually grow this before launch).
- [ ] **Build the personalized-proof paywall** on live data: "N free items worth ~$X within 10 mi — unlock to see exactly where." It does not exist yet; it's your single highest-leverage conversion asset. **Cap the headline number to what live data supports** (today the entire DB is $2,575 — a "$1,240 in your city" claim is currently false; show the real nearest-metro number or "expanding to your area," never a fabricated figure).
- [ ] **FIX THE VALUATION before any creator films it.** This is the existential blind spot. Either (a) wire even a crude eBay/Marketplace **sold-comp** lookup, or (b) **relabel** the number "est. resale range," not "profit" — ideally both. Today it's a regex (`valuation.py`); the first creator who films it over-valuing an item turns your proof into debunk content.
- [ ] **Build referral/attribution** (absent today): a `referral_code` on `profiles` + signup capture + per-creator code → attributed subs. Without it you cannot pay commission, attribute wins, or run the "creators paid only if it converts" honesty mechanism.
- [ ] **Hide/gate any mock screens** (Scanner/Map/GPS/push) so a 500k-view audience poking every tab doesn't generate "this app is fake" comments.
- **Gate:** a stranger in a launch metro can install → onboard → see real local proof → start a $9.99 monthly sub or 3-day annual trial → money lands → a referral link works. If any link is broken, Week 1 does not start.

### Week 1 — Recruit + arm (sustainable pace)
- DM **~60-75 creators** (target ~120-150 over weeks 1-2), sourced from r/Flipping, r/flea_market, r/sidehustle cross-posters; TikTok/Shorts search "thrift flip," "free find," "curb alert," "Marketplace flips"; reseller Discords/FB groups.
- **The offer / exact DM angle (lead with inventory, not sponsorship):** *"I built an app that finds free stuff worth real money near you — it makes the exact videos you already make, but I hand you the inventory. There's a free [item] worth ~$X about 8 min from you right now. Want a free Pro account + 30% recurring commission on anyone who subscribes with your code? You post 2 videos; if it flops you've lost 20 minutes."* Free lifetime Pro (zero marginal cost) + **30% recurring affiliate** + **done-for-you pre-scouted local inventory** + small flat fee ($75-200) for the 3-5 mid anchors only.
- Founder account: start posting **1-2 real flips/day**. Format below.

### Week 2 — Wave + measure the funnel for real
- Posters go live in a **loose 72-96h cluster** (tight enough to read as momentum, loose enough to avoid coordinated-posting suppression — vary hooks/captions, no copy-paste). Mid anchors land mid-window.
- **This is the moment assumptions become data.** Instrument install→paywall→pay. **Kill-switch: if install→pay <1.5% on the first ~500 installs, STOP scaling and fix the paywall/proof before recruiting or spending another dollar.** A breakout on a leaky funnel is worse than no breakout.
- Founder seeding loop: comment/stitch/duet creator posts from the brand account; reply to every "is this real?" with a real local find.

### Week 3 — Amplify winners, deploy the only paid dollars
- Identify the 5-10 posts pulling 50k+ views. Ask for part-2s.
- **Surgical paid (the one defensible use of a tiny budget):** hold $300-1,500 until something wins organically. Then put $300-900 of **TikTok Spark Ads behind the single best organic post, run as the creator's handle** (far higher trust than a brand ad for a scam-skeptical audience). This *amplifies a proven winner* — it is NOT a cold-start channel and buys only ~20-160 incremental subs, so it converts a near-miss to a hit, it can't rescue a campaign with no winner. **If nothing pops, don't spend — hold the cash.**

### Weeks 4-6 — Compound or pivot
- Recruit a second small cohort using week-2 receipts as proof ("here's a creator who earned $X in commission in 3 days").
- Layer the **in-app referral loop** (your users ARE resellers with audiences) — the only channel that compounds without you.
- Attack churn: weekly fresh-find FCM push so the radar stays useful past the first flip (monthly churn after one flip is a real MRR leak). Push annual to engaged monthly users ("you've claimed 4 items — lock in a year").
- **Communities (parallel, $0):** post real finds (not links) into r/Flipping, r/flea_market, r/sidehustle, reseller FB/Discord — value first, app mentioned only when asked. Lead skeptic content head-on ("yes, another 'make money' app — but the stuff is free and real, watch").

**The hook formula (give verbatim to every creator):** 0-2s visual+number ("This was FREE on Marketplace 20 min ago — worth $180"); 2-6s mechanism (show the radar/map screen); 6-12s proof (real listing + value + radius); 12-20s payoff (pickup → resale); soft CTA pre-empting the price ("It's Freebox, link in bio — ten bucks a month, one flip pays for the year"). Ban "check out this app" openers.

---

## 5. The conditions that MUST be true

1. **Product is true and chargeable before outreach.** Billing live, scraper producing real dense inventory in the 2-3 launch metros, personalized paywall on live data, valuation relabeled/comped, referral attribution built. *Verified today: none of these are done.* This is the gate; without it the odds are ~0%, not 8-15%.
2. **install→pay ≥ ~3% (validate week 1; abort-fix if <1.5%).** The most fragile number in the funnel and the likeliest failure. Maximize it with the personalized-proof screen, in-video price pre-emption, and (last resort, A/B it) a "see ONE free find, then pay" tease.
3. **Local supply density in launch metros.** A referred user who sees an empty radar refunds, 1-stars "SCAM," and comments it under the creator's video — poisoning the channel, and a refund/chargeback spike on a new Play account risks suspension. Gate the campaign to metros you've verified; pre-warm `crawl_targets` for your biggest creators' audience ZIPs *before* their posts drop.
4. **Valuation survives expert scrutiny.** The audience is r/Flipping. Comp or relabel the number, or the self-demonstrating proof becomes self-demonstrating debunk.
5. **At least one breakout OR a strong base case.** ~480 posts isn't on the table solo; ~15-25 posters + founder daily means you need the base case to land near the top of its range, or a couple of the mid anchors to break. Load the dice (hook discipline, niche-match, loose cluster, save-worthy payoff) — you cannot guarantee the roll.

---

## 6. Honest odds & fallback

**This is a power-law bet with an engineerable floor, not a reliably engineerable 45-day outcome.** Probability of hitting the full **~$3-5k MRR in 30-45 days, from 0, solo, near-zero budget: ~8-15%** — and that band *only* applies once Week-0 blockers are closed (before that, ~0%). My band sits **below** most of the candidate designs' claimed 15-30% for three verified reasons they under-weighted: the conversion-engine paywall **doesn't exist yet**, listing density is a **9-city, max-2-per-city, $2,575-total desert** that gates conversion before the funnel starts, and the valuation is an **unvalidated regex** aimed at the most expert-skeptical audience alive. I distrust any design that reached a higher band by stacking the two rosiest rates (1.5% view→install + 12% paywall conversion) — believe the mechanic, not that number.

**Most likely real outcome (modal, ~50-60% of the probability mass):** ~$1-2.5k MRR / ~80-250 subs by day 45. A real, validated business — just not the headline.
**Downside (~20-30%):** install→pay craters below 1.5%, or the scraper/billing slips, or no post clears 50k → <$1k MRR.
**Upside (~8-15%):** a couple of mid-anchor breakouts + the funnel holds → target hit.

**Leading indicators to watch in the first 14 days** (these tell you which tail you're in before you've spent the budget):
- **install→pay on first ~500 installs.** ≥3% = on track; <1.5% = stop and fix.
- **At least one post clearing ~50k organic views** by ~day 10. If nothing crosses 50k, the breakout isn't coming on schedule.
- **DM→post ≥ ~10%.** If <5%, sweeten the offer (higher % or small upfront) before blaming the algorithm.
- **Refund rate / "empty radar" comments.** Any cluster means a metro is too thin — pull the campaign there immediately.
- **3-day annual trial→paid ≥ ~45%.** Below that, fix the trial-end screen; the cash target misses even if MRR is fine.

**Realistic fallback (when the breakout doesn't land by ~day 20-25):** stop trying to *manufacture* a viral moment; switch to the *compounding flywheel*. Keep the best 8-15 creators on affiliate retainer posting weekly, concentrate on the 2-3 metros where install→pay is proven, layer the in-app referral loop, and reinvest the first ~$3-5k collected into a second, better-produced round in your best-converting metros plus Spark Ads behind now-proven creatives. The creator engine, content library, referral loop, and — crucially — the *measured* funnel rates you now own (replacing every assumption above) don't die; they amortize. **Honest fallback timeline to $3-5k MRR: 75-120 days, at a meaningfully higher ~50-60% probability than the 45-day shot.** Run the 45-day blitz as the cheap ticket to the tail; build the machine so the base case is a launchpad, not a failure.

---

### What I'd tell the founder in one line
Fix the price string, make the radar true in three cities, relabel the valuation, build billing + referral — *then* arm 15-25 flipping creators with free inventory and a commission, post your own flips daily, measure install→pay in week one, and hold your paid dollars until something wins. Hitting $4.5k MRR in 45 days is a ~1-in-8-to-1-in-7 shot you've loaded as hard as a solo founder honestly can; landing ~$1.5-2.5k MRR and a real, measured growth machine is the base case — and it's the thing that gets you to $3-5k by day ~90.