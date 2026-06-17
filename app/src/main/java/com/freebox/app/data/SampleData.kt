package com.freebox.app.data

/**
 * Returns true when [item] passes all active [SearchFilters].
 * Empty/default filter values always pass (show-everything semantics).
 *
 * Quick-select semantics:
 *   "High Margin"  -> profitValue >= 200
 *   "Fast Flip"    -> distanceMiles <= 5
 *   "Rare"         -> no-op placeholder (maps to a backend rarity flag in Phase 5)
 */
fun matchesFilters(item: LootItem, filters: SearchFilters): Boolean {
    val defaultSources = SearchFilters().sources
    if (item.profitValue < filters.minProfit) return false
    if (item.distanceMiles > filters.radiusMiles) return false
    if (filters.sources != defaultSources && item.sourceName !in filters.sources) return false
    if ("High Margin" in filters.quickSelects && item.profitValue < 200) return false
    if ("Fast Flip" in filters.quickSelects && item.distanceMiles > 5.0) return false
    // "Rare" -> backend rarity flag; no local predicate yet
    return true
}

data class LootItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val timeAgo: String,
    val estProfit: String,
    val resaleValue: String,
    val condition: String,
    val distanceAway: String,
    val finderName: String,
    val finderNote: String,
    // Filter-queryable fields — mirrors future `listings` table columns.
    val sourceName: String,      // "Facebook Marketplace" | "Craigslist" | "OfferUp"
    val distanceMiles: Double,   // numeric form of distanceAway
    val profitValue: Int         // numeric form of estProfit (dollars)
)

object SampleData {
    val lootItems = listOf(
        LootItem(
            id = "eames-lounge-chair",
            title = "Eames Style Lounge Chair & Ottoman",
            description = "Curbside find in great shape. Genuine leather cushions with minor wear on the ottoman, walnut veneer shell intact.",
            category = "Furniture",
            location = "Oakland, CA",
            timeAgo = "12 mins ago",
            estProfit = "+$350",
            resaleValue = "$350",
            condition = "Good",
            distanceAway = "0.8 mi away",
            finderName = "Alex T. (Top Contributor)",
            finderNote = "Spotted on the curb this morning. Owner confirmed it's free to whoever picks it up first. Bring a truck — it's heavier than it looks.",
            sourceName = "Facebook Marketplace",
            distanceMiles = 0.8,
            profitValue = 350
        ),
        LootItem(
            id = "marantz-turntable",
            title = "1970s Marantz Wood Turntable",
            description = "Excellent condition vintage turntable found at a local estate sale. Needs minor belt replacement but otherwise fully functional.",
            category = "Electronics",
            location = "Portland, OR",
            timeAgo = "2 hours ago",
            estProfit = "+$150",
            resaleValue = "$150",
            condition = "Excellent",
            distanceAway = "1.2 mi away",
            finderName = "Jordan M.",
            finderNote = "Estate sale leftovers on the porch. Belt is stretched but the motor spins clean.",
            sourceName = "Craigslist",
            distanceMiles = 1.2,
            profitValue = 150
        ),
        LootItem(
            id = "canon-ae1",
            title = "Canon AE-1 Program (Body Only)",
            description = "Untested but cosmetically perfect Canon AE-1 found in a charity shop. Battery door is intact.",
            category = "Photography",
            location = "Austin, TX",
            timeAgo = "5 hours ago",
            estProfit = "+$85",
            resaleValue = "$85",
            condition = "Untested",
            distanceAway = "2.4 mi away",
            finderName = "Sam R.",
            finderNote = "Charity shop free bin. Shutter fires at all speeds by ear.",
            sourceName = "OfferUp",
            distanceMiles = 2.4,
            profitValue = 85
        ),
        LootItem(
            id = "macintosh-plus",
            title = "Apple Macintosh Plus 1MB",
            description = "Rare find with original keyboard and mouse. Powers on with a question mark disk icon.",
            category = "Computing",
            location = "Seattle, WA",
            timeAgo = "1 day ago",
            estProfit = "+$200",
            resaleValue = "$200",
            condition = "Powers On",
            distanceAway = "3.1 mi away",
            finderName = "Casey L.",
            finderNote = "Office cleanout. Includes original keyboard and mouse, no disks.",
            sourceName = "Craigslist",
            distanceMiles = 3.1,
            profitValue = 200
        )
    )

    fun itemById(id: String): LootItem? = lootItems.find { it.id == id }
}
