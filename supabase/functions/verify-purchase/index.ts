// verify-purchase — server-side Google Play purchase verification (the paywall's
// security boundary). The client sends a Play purchase token after a successful
// Billing flow; this function validates it against the Google Play Developer API
// and writes the authoritative entitlements row (service-role). The client's local
// entitlement state is advisory only — access is gated by RLS on this row.
//
// Required secrets (set with `supabase secrets set`):
//   GOOGLE_PLAY_SA_JSON        — a Google Cloud service-account JSON with the
//                                Android Publisher API enabled and access granted
//                                in Play Console (Users & permissions).
//   GOOGLE_PLAY_PACKAGE_NAME   — e.g. com.freebox.app
// (SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY are injected by the platform.)
//
// Until those Play secrets exist (i.e. before Play Console is set up) the function
// returns 503 rather than failing open — the paywall stays closed.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });

  try {
    const jwt = (req.headers.get("Authorization") ?? "").replace("Bearer ", "").trim();
    if (!jwt) return json({ error: "missing Authorization" }, 401);

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const admin = createClient(supabaseUrl, serviceKey);

    // Resolve the caller from their JWT (service-role getUser verifies the token).
    const { data: userData, error: userErr } = await admin.auth.getUser(jwt);
    if (userErr || !userData?.user) return json({ error: "invalid auth" }, 401);
    const userId = userData.user.id;

    const { purchaseToken, productId } = await req.json().catch(() => ({}));
    if (!purchaseToken) return json({ error: "purchaseToken required" }, 400);

    const saJson = Deno.env.get("GOOGLE_PLAY_SA_JSON");
    const pkg = Deno.env.get("GOOGLE_PLAY_PACKAGE_NAME");
    if (!saJson || !pkg) {
      // Not yet configured (pre-Play-Console). Fail CLOSED — never grant on misconfig.
      return json({ error: "play_verification_not_configured" }, 503);
    }

    const accessToken = await getPlayAccessToken(JSON.parse(saJson));
    const url =
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
      `${pkg}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
    const resp = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });
    if (!resp.ok) {
      return json({ error: "play_verify_failed", status: resp.status, body: await resp.text() }, 502);
    }
    const sub = await resp.json();

    // subscriptionState: ACTIVE / IN_GRACE_PERIOD / ON_HOLD / PAUSED / CANCELED / EXPIRED
    const state: string = sub.subscriptionState ?? "";
    const active = state === "SUBSCRIPTION_STATE_ACTIVE" ||
      state === "SUBSCRIPTION_STATE_IN_GRACE_PERIOD";

    const lineItems: Array<{ expiryTime?: string; productId?: string }> =
      Array.isArray(sub.lineItems) ? sub.lineItems : [];
    const expiresAt = lineItems.map((x) => x.expiryTime).filter(Boolean).sort().pop() ?? null;
    const resolvedProductId = productId ?? lineItems[0]?.productId ?? null;

    const { error: upErr } = await admin.from("entitlements").upsert({
      user_id: userId,
      status: active ? "active" : "expired",
      product_id: resolvedProductId,
      play_purchase_token: purchaseToken,
      expires_at: expiresAt,
      updated_at: new Date().toISOString(),
    }, { onConflict: "user_id" });
    if (upErr) return json({ error: "db_upsert_failed", detail: upErr.message }, 500);

    return json({ entitled: active, status: active ? "active" : "expired", expires_at: expiresAt });
  } catch (e) {
    return json({ error: String(e) }, 500);
  }
});

// ── Google service-account → OAuth2 access token (RS256 JWT bearer grant) ──
async function getPlayAccessToken(sa: { client_email: string; private_key: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const enc = (o: unknown) => b64url(new TextEncoder().encode(JSON.stringify(o)));
  const unsigned = `${enc(header)}.${enc(claim)}`;
  const key = await importPkcs8(sa.private_key);
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  const assertion = `${unsigned}.${b64url(new Uint8Array(sig))}`;

  const resp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!resp.ok) throw new Error("oauth_token_exchange_failed: " + (await resp.text()));
  return (await resp.json()).access_token as string;
}

function b64url(bytes: Uint8Array): string {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function importPkcs8(pem: string): Promise<CryptoKey> {
  const body = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    der.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}
