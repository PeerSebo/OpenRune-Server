package org.rsmod.content.other.grandexchange

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.min

class GeExchange(private val items: GeItemData, private val clock: () -> Long) {
    @Inject constructor(items: GeItemData) : this(items, System::currentTimeMillis)

    interface Listener {
        fun onOfferTraded(offer: GeOffer, trade: GeTrade)

        fun onOfferFinished(offer: GeOffer, aborted: Boolean)
    }

    var listener: Listener? = null

    private val offers = LinkedHashMap<Long, GeOffer>()
    private val slotsByOwner = HashMap<Int, Array<GeOffer?>>()
    private val activeByItem = HashMap<Int, MutableList<GeOffer>>()
    private val history = HashMap<Int, ArrayDeque<GeTrade>>()
    private val buyWindows = HashMap<Int, HashMap<Int, GeBuyWindow>>()
    private var nextId = 1L

    var dirty: Boolean = false
        private set

    val offerCount: Int
        get() = offers.size

    fun allOffers(): List<GeOffer> = offers.values.toList()

    fun slots(owner: Int): List<GeOffer?> = slotsByOwner[owner]?.toList() ?: List(GeConfig.SLOT_COUNT) { null }

    fun offer(owner: Int, slot: Int): GeOffer? = slotsByOwner[owner]?.getOrNull(slot)

    fun firstEmptySlot(owner: Int): Int? {
        val slots = slotsByOwner[owner] ?: return 0
        return slots.indices.firstOrNull { slots[it] == null }
    }

    fun history(owner: Int): List<GeTrade> = history[owner]?.toList() ?: emptyList()

    fun remainingBuyLimit(owner: Int, item: Int, now: Long = clock()): Int {
        val limit = items.buyLimit(item)
        if (limit == GeItemData.NO_LIMIT) {
            return limit
        }
        val window = buyWindows[owner]?.get(item) ?: return limit
        if (now - window.start >= GeConfig.BUY_LIMIT_WINDOW_MILLIS) {
            return limit
        }
        return maxOf(0, limit - window.bought)
    }

    fun buyLimitResetIn(owner: Int, item: Int, now: Long = clock()): Long {
        val window = buyWindows[owner]?.get(item) ?: return 0
        val remaining = GeConfig.BUY_LIMIT_WINDOW_MILLIS - (now - window.start)
        return if (remaining > 0) remaining else 0
    }

    fun place(
        owner: Int,
        ownerName: String,
        slot: Int,
        type: GeOfferType,
        item: Int,
        price: Int,
        quantity: Int,
    ): GeOffer {
        require(slot in 0 until GeConfig.SLOT_COUNT) { "Invalid slot: $slot" }
        require(price > 0) { "Price must be positive: $price" }
        require(quantity > 0) { "Quantity must be positive: $quantity" }
        require(price.toLong() * quantity <= Int.MAX_VALUE) { "Offer value overflows: $price x $quantity" }
        val slots = slotsByOwner.getOrPut(owner) { arrayOfNulls(GeConfig.SLOT_COUNT) }
        require(slots[slot] == null) { "Slot $slot of $owner is occupied" }

        val offer = GeOffer(nextId++, owner, ownerName, slot, type, item, price, quantity, clock())
        offers[offer.id] = offer
        slots[slot] = offer
        activeByItem.getOrPut(item) { ArrayList() } += offer
        dirty = true
        match(offer)
        return offer
    }

    fun abort(offer: GeOffer): Boolean {
        if (offer.finished) {
            return false
        }
        val remaining = offer.remaining
        when (offer.type) {
            GeOfferType.Buy -> offer.collectCoins += remaining * offer.price
            GeOfferType.Sell -> offer.collectItems += remaining
        }
        finish(offer, aborted = true)
        return true
    }

    fun takeItems(offer: GeOffer, count: Int = offer.collectItems): Int {
        val taken = count.coerceIn(0, offer.collectItems)
        offer.collectItems -= taken
        dirty = true
        releaseIfDone(offer)
        return taken
    }

    fun takeCoins(offer: GeOffer, count: Int = offer.collectCoins): Int {
        val taken = count.coerceIn(0, offer.collectCoins)
        offer.collectCoins -= taken
        dirty = true
        releaseIfDone(offer)
        return taken
    }

    private fun match(offer: GeOffer) {
        val active = activeByItem[offer.item] ?: return
        val candidates =
            active
                .filter { it !== offer && it.type != offer.type && it.isActive && it.remaining > 0 }
                .filter { other ->
                    if (offer.type == GeOfferType.Buy) other.price <= offer.price else other.price >= offer.price
                }
                .sortedWith(
                    if (offer.type == GeOfferType.Buy) {
                        compareBy<GeOffer>({ it.price }, { it.createdAt }, { it.id })
                    } else {
                        compareBy<GeOffer>({ -it.price }, { it.createdAt }, { it.id })
                    }
                )
        for (other in candidates) {
            if (offer.remaining <= 0) {
                break
            }
            val buy = if (offer.type == GeOfferType.Buy) offer else other
            val sell = if (offer.type == GeOfferType.Sell) offer else other
            var quantity = min(buy.remaining, sell.remaining)
            quantity = min(quantity, remainingBuyLimit(buy.owner, buy.item))
            // A seller can receive more than their asking price. Keep the collection stack
            // within the inventory limit even across many separate high-priced buy offers.
            val price = if (offer.id < other.id) offer.price else other.price
            val netPerItem = price - items.taxPerItem(buy.item, price)
            if (netPerItem > 0) {
                quantity = min(quantity, (Int.MAX_VALUE - sell.collectCoins) / netPerItem)
            }
            if (quantity <= 0) {
                if (buy === offer && remainingBuyLimit(buy.owner, buy.item) <= 0) {
                    break
                }
                continue
            }
            trade(buy, sell, quantity, price)
        }
    }

    fun matchWaitingOffers() {
        for (offer in offers.values.filter { it.isActive && it.type == GeOfferType.Buy }) {
            if (offer.isActive && remainingBuyLimit(offer.owner, offer.item) > 0) {
                match(offer)
            }
        }
    }

    private fun trade(buy: GeOffer, sell: GeOffer, quantity: Int, price: Int) {
        val now = clock()
        val gross = quantity * price
        val tax = items.taxPerItem(buy.item, price) * quantity

        buy.completed += quantity
        buy.gold += gross
        buy.collectItems += quantity
        buy.collectCoins += quantity * (buy.price - price)

        sell.completed += quantity
        // These are client display counters; the escrow stack below retains every payable coin.
        sell.gold = min(Int.MAX_VALUE.toLong(), sell.gold.toLong() + gross).toInt()
        sell.taxPaid = min(Int.MAX_VALUE.toLong(), sell.taxPaid.toLong() + tax).toInt()
        sell.collectCoins += gross - tax

        recordPurchase(buy.owner, buy.item, quantity, now)
        val bought = GeTrade(now, GeOfferType.Buy, buy.item, quantity, gross, 0)
        val sold = GeTrade(now, GeOfferType.Sell, sell.item, quantity, gross, tax)
        addHistory(buy.owner, bought)
        addHistory(sell.owner, sold)
        dirty = true

        listener?.onOfferTraded(buy, bought)
        listener?.onOfferTraded(sell, sold)
        if (buy.remaining <= 0) {
            finish(buy, aborted = false)
        }
        if (sell.remaining <= 0) {
            finish(sell, aborted = false)
        }
    }

    private fun finish(offer: GeOffer, aborted: Boolean) {
        offer.finished = true
        activeByItem[offer.item]?.remove(offer)
        dirty = true
        listener?.onOfferFinished(offer, aborted)
        releaseIfDone(offer)
    }

    private fun releaseIfDone(offer: GeOffer) {
        if (!offer.finished || offer.hasCollectables) {
            return
        }
        offers.remove(offer.id)
        slotsByOwner[offer.owner]?.let { slots ->
            if (slots.getOrNull(offer.slot) === offer) {
                slots[offer.slot] = null
            }
        }
        dirty = true
    }

    private fun recordPurchase(owner: Int, item: Int, quantity: Int, now: Long) {
        if (items.buyLimit(item) == GeItemData.NO_LIMIT) {
            return
        }
        val windows = buyWindows.getOrPut(owner) { HashMap() }
        val window = windows[item]
        windows[item] =
            if (window == null || now - window.start >= GeConfig.BUY_LIMIT_WINDOW_MILLIS) {
                GeBuyWindow(now, quantity)
            } else {
                window.copy(bought = window.bought + quantity)
            }
    }

    private fun addHistory(owner: Int, trade: GeTrade) {
        val list = history.getOrPut(owner) { ArrayDeque() }
        list.addFirst(trade)
        while (list.size > GeConfig.HISTORY_SIZE) {
            list.removeLast()
        }
    }

    fun save(path: Path) {
        val data =
            GeSaveData(
                nextId = nextId,
                offers = offers.values.map(GeOfferData::from),
                history = history.mapValues { it.value.toList() },
                buyWindows =
                    buyWindows.flatMap { (owner, windows) ->
                        windows.map { (item, window) -> GeBuyWindowData(owner, item, window.start, window.bought) }
                    },
            )
        Files.createDirectories(path.toAbsolutePath().parent)
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), data)
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        dirty = false
    }

    fun saveIfDirty(path: Path) {
        if (dirty) {
            save(path)
        }
    }

    fun load(path: Path): Boolean {
        if (!Files.exists(path)) {
            return false
        }
        val data = mapper.readValue<GeSaveData>(path.toFile())
        // Validate the whole snapshot before mutating the live book.
        require(data.nextId > 0) { "Invalid Grand Exchange sequence" }
        require(data.offers.map { it.id }.toSet().size == data.offers.size) { "Duplicate offer id" }
        require(data.offers.map { it.owner to it.slot }.toSet().size == data.offers.size) {
            "Duplicate Grand Exchange owner slot"
        }
        for (offer in data.offers) {
            require(offer.slot in 0 until GeConfig.SLOT_COUNT && offer.id > 0)
            require(offer.price > 0 && offer.total > 0 && offer.price.toLong() * offer.total <= Int.MAX_VALUE)
            require(offer.completed in 0..offer.total)
            require(offer.gold >= 0 && offer.taxPaid >= 0 && offer.collectItems >= 0 && offer.collectCoins >= 0)
        }
        offers.clear()
        slotsByOwner.clear()
        activeByItem.clear()
        history.clear()
        buyWindows.clear()
        nextId = data.nextId
        for (saved in data.offers) {
            val offer = saved.toOffer()
            offers[offer.id] = offer
            slotsByOwner.getOrPut(offer.owner) { arrayOfNulls(GeConfig.SLOT_COUNT) }[offer.slot] = offer
            if (offer.isActive) {
                activeByItem.getOrPut(offer.item) { ArrayList() } += offer
            }
            if (offer.id >= nextId) {
                nextId = offer.id + 1
            }
        }
        for ((owner, trades) in data.history) {
            history[owner] = ArrayDeque(trades.take(GeConfig.HISTORY_SIZE))
        }
        for (window in data.buyWindows) {
            buyWindows.getOrPut(window.owner) { HashMap() }[window.item] = GeBuyWindow(window.start, window.bought)
        }
        dirty = false
        logger.debug { "Loaded ${offers.size} Grand Exchange offers from $path." }
        return true
    }

    private companion object {
        private val logger = InlineLogger()
        private val mapper = jacksonObjectMapper()
    }
}

data class GeSaveData(
    val nextId: Long = 1,
    val offers: List<GeOfferData> = emptyList(),
    val history: Map<Int, List<GeTrade>> = emptyMap(),
    val buyWindows: List<GeBuyWindowData> = emptyList(),
)

data class GeBuyWindowData(val owner: Int = 0, val item: Int = 0, val start: Long = 0, val bought: Int = 0)

data class GeOfferData(
    val id: Long = 0,
    val owner: Int = 0,
    val ownerName: String = "",
    val slot: Int = 0,
    val type: GeOfferType = GeOfferType.Buy,
    val item: Int = 0,
    val price: Int = 0,
    val total: Int = 0,
    val createdAt: Long = 0,
    val completed: Int = 0,
    val gold: Int = 0,
    val taxPaid: Int = 0,
    val collectItems: Int = 0,
    val collectCoins: Int = 0,
    val finished: Boolean = false,
) {
    fun toOffer(): GeOffer =
        GeOffer(id, owner, ownerName, slot, type, item, price, total, createdAt).also {
            it.completed = completed
            it.gold = gold
            it.taxPaid = taxPaid
            it.collectItems = collectItems
            it.collectCoins = collectCoins
            it.finished = finished
        }

    companion object {
        fun from(offer: GeOffer): GeOfferData =
            GeOfferData(
                id = offer.id,
                owner = offer.owner,
                ownerName = offer.ownerName,
                slot = offer.slot,
                type = offer.type,
                item = offer.item,
                price = offer.price,
                total = offer.total,
                createdAt = offer.createdAt,
                completed = offer.completed,
                gold = offer.gold,
                taxPaid = offer.taxPaid,
                collectItems = offer.collectItems,
                collectCoins = offer.collectCoins,
                finished = offer.finished,
            )
    }
}
