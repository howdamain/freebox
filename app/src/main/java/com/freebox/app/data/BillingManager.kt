package com.freebox.app.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import io.github.jan.supabase.functions.functions
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Client side of the Play Billing flow. The security boundary is server-side:
 * after Play reports a purchase we send the token to the `verify-purchase` Edge
 * Function, which validates it against the Google Play Developer API and writes
 * the authoritative `entitlements` row. The client never grants entitlement —
 * it only triggers a re-check (EntitlementChanged) after verification.
 *
 * Play Console setup this expects:
 *   - one SUBSCRIPTION product id [SUB_PRODUCT_ID]
 *   - two base plans on it: [BASE_PLAN_MONTHLY] and [BASE_PLAN_ANNUAL]
 *   (free trial, if any, is configured as an offer on the annual base plan).
 */
object BillingManager {

    const val SUB_PRODUCT_ID = "freebox_pro"
    const val BASE_PLAN_MONTHLY = "monthly"
    const val BASE_PLAN_ANNUAL = "annual"

    private const val TAG = "BillingManager"

    sealed interface BillingEvent {
        /** A purchase was verified/acknowledged — the gate should re-check entitlement. */
        data object EntitlementChanged : BillingEvent
        data class Error(val message: String) : BillingEvent
    }

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var client: BillingClient? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { verifyAndAcknowledge(it) }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                Unit // user backed out — stay on the paywall, no error
            else ->
                _events.tryEmit(BillingEvent.Error("Purchase failed (code ${result.responseCode})"))
        }
    }

    /** Idempotent. Connects to Play and re-syncs existing purchases on connect. */
    fun start(context: Context) {
        if (client?.isReady == true) return
        val c = BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refreshPurchases()
            }
            override fun onBillingServiceDisconnected() {
                // Reconnect lazily on next launchPurchase/refresh.
            }
        })
    }

    /** Launch the Play purchase dialog for the given base plan. */
    suspend fun launchPurchase(activity: Activity, basePlanId: String) {
        val c = client
        if (c == null || !c.isReady) {
            _events.tryEmit(BillingEvent.Error("Billing not ready — try again in a moment"))
            return
        }
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SUB_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        val details = c.queryProductDetails(query).productDetailsList?.firstOrNull()
        if (details == null) {
            _events.tryEmit(
                BillingEvent.Error("Subscription '$SUB_PRODUCT_ID' not found — set it up in Play Console")
            )
            return
        }
        val offerToken = pickOfferToken(details, basePlanId)
        if (offerToken == null) {
            _events.tryEmit(BillingEvent.Error("No offer found for base plan '$basePlanId'"))
            return
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        c.launchBillingFlow(activity, flowParams)
    }

    /** Re-verify any active subscriptions Play already knows about (restore path). */
    fun refreshPurchases() {
        val c = client ?: return
        scope.launch {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val result = c.queryPurchasesAsync(params)
            result.purchasesList.forEach { verifyAndAcknowledge(it) }
        }
    }

    // Prefer an offer with a free pricing phase (trial) for the chosen base plan,
    // else the base-plan offer itself.
    private fun pickOfferToken(details: ProductDetails, basePlanId: String): String? {
        val offers = details.subscriptionOfferDetails?.filter { it.basePlanId == basePlanId }
            ?: emptyList()
        if (offers.isEmpty()) return details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        val withTrial = offers.maxByOrNull { offer ->
            offer.pricingPhases.pricingPhaseList.count { it.priceAmountMicros == 0L }
        }
        return (withTrial ?: offers.first()).offerToken
    }

    private fun verifyAndAcknowledge(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        scope.launch {
            Analytics.track(
                "purchase_completed",
                mapOf("product" to (purchase.products.firstOrNull() ?: SUB_PRODUCT_ID))
            )
            // 1. Server-side verification — writes the authoritative entitlements row.
            val verified = runCatching {
                supabase.functions.invoke("verify-purchase") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        buildJsonObject {
                            put("purchaseToken", purchase.purchaseToken)
                            put("productId", purchase.products.firstOrNull() ?: SUB_PRODUCT_ID)
                        }
                    )
                }
            }
            verified.onFailure {
                Log.w(TAG, "verify-purchase failed: ${it.message}")
                _events.tryEmit(BillingEvent.Error("Couldn't verify purchase — it'll retry on next launch"))
            }
            // 2. Acknowledge (Play auto-refunds unacknowledged purchases after 3 days).
            if (!purchase.isAcknowledged) {
                runCatching {
                    client?.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                    )
                }.onFailure { Log.w(TAG, "acknowledge failed: ${it.message}") }
            }
            // 3. Tell the app to re-read entitlement (the DB now reflects verification).
            _events.tryEmit(BillingEvent.EntitlementChanged)
        }
    }
}
