package com.awgconfig.android.core.routes

import org.json.JSONObject
import java.io.InputStream

/**
 * Каталог пресетов маршрутов: мета (порт routePresets.js донора) + офлайн-набор CIDR
 * из ассета routes.json (снапшот каталога донора). Агрегация, вычитание подсетей
 * endpoint'ов WARP и лимит 1000 IPv4 CIDR.
 */
object RouteCatalog {

    data class PresetMeta(
        val key: String,
        val labelRu: String,
        val labelEn: String,
        val category: String,
        val domains: List<String>,
    )

    val CATEGORY_ORDER = listOf(
        "video", "messengers", "social", "torrent", "media", "gaming", "ai", "work", "infra", "other",
    )

    fun categoryRu(category: String): String = when (category) {
        "video" -> "Видео"; "messengers" -> "Мессенджеры и звонки"; "social" -> "Соцсети"
        "torrent" -> "Торренты"; "media" -> "Кино, музыка и аниме"; "gaming" -> "Игры"
        "ai" -> "ИИ"; "work" -> "Работа и разработка"; "infra" -> "Инфраструктура"
        else -> "Прочее"
    }

    fun categoryEn(category: String): String = when (category) {
        "video" -> "Video"; "messengers" -> "Messengers & calls"; "social" -> "Social"
        "torrent" -> "Torrents"; "media" -> "Movies, music & anime"; "gaming" -> "Gaming"
        "ai" -> "AI"; "work" -> "Work & dev"; "infra" -> "Infrastructure"
        else -> "Other"
    }

    /** Кнопка «Основной набор» (PRESET_QUICK_PICK донора). */
    val QUICK_PICK = listOf("youtube", "telegram", "discord", "cloudflare", "twitter", "github", "rutracker")

    private val PRESET_ALIASES = mapOf("rutracker_local" to "rutracker")

    private fun p(
        key: String, ru: String, en: String, category: String, domains: List<String>,
    ) = PresetMeta(key, ru, en, category, domains)

    val PRESETS: List<PresetMeta> = listOf(
        p("youtube", "YouTube", "YouTube", "video", listOf("youtube.com", "youtu.be", "googlevideo.com", "ytimg.com", "ggpht.com")),
        p("twitch", "Twitch", "Twitch", "video", listOf("twitch.tv", "ttvnw.net", "jtvnw.net")),
        p("telegram", "Telegram", "Telegram", "messengers", listOf("telegram.org")),
        p("discord", "Discord", "Discord", "messengers", listOf("discord.com", "discord.gg", "discord.media", "discordapp.com", "discordapp.net")),
        p("whatsapp", "WhatsApp", "WhatsApp", "messengers", listOf("whatsapp.com", "web.whatsapp.com", "cdn.whatsapp.net", "graph.whatsapp.com")),
        p("viber", "Viber", "Viber", "messengers", listOf("viber.com", "account.viber.com", "media.cdn.viber.com", "activate.viber.com")),
        p("signal", "Signal", "Signal", "messengers", listOf("signal.org")),
        p("instagram", "Instagram", "Instagram", "social", listOf("instagram.com", "cdninstagram.com", "fbcdn.net")),
        p("facebook", "Facebook", "Facebook", "social", listOf("facebook.com", "fb.com", "fbsbx.com")),
        p("twitter", "X (Twitter)", "X (Twitter)", "social", listOf("twitter.com", "x.com", "twimg.com", "t.co")),
        p("linkedin", "LinkedIn", "LinkedIn", "social", listOf("linkedin.com", "licdn.com")),
        p("snapchat", "Snapchat", "Snapchat", "social", listOf("snapchat.com")),
        p("reddit", "Reddit", "Reddit", "social", listOf("reddit.com", "redditmedia.com", "redd.it")),
        p("tiktok", "TikTok", "TikTok", "social", listOf("tiktok.com", "tiktokcdn.com", "ttlivecdn.com")),
        p("rutracker", "RuTracker", "RuTracker", "torrent", listOf("rutracker.org", "rutracker.net", "bt.t-ru.org", "rutracker.cc")),
        p("rutor", "Rutor", "Rutor", "torrent", listOf("rutor.org", "rutor.info", "rutor.is", "rutor.lib")),
        p("nnmclub", "NNM-Club", "NNM-Club", "torrent", listOf("nnmclub.to", "nnm-club.me", "nnmclub.ro", "nnm-club.info")),
        p("kinozal", "Kinozal", "Kinozal", "torrent", listOf("kinozal.tv", "kinozal.me", "kinozal.guru")),
        p("tapochek", "Tapochek", "Tapochek", "torrent", listOf("tapochek.net", "tapochek.club")),
        p("torrent_more", "Другие трекеры", "Other trackers", "torrent", listOf("1337x.to", "thepiratebay.org", "rustorka.com", "newstudio.tv", "booktracker.org", "nyaa.si")),
        p("hdrezka", "HDrezka", "HDrezka", "media", listOf("hdrezka.ag", "hdrezka.me", "rezka.ag", "voidboost.net", "rezka.cc")),
        p("animego", "AnimeGo", "AnimeGo", "media", listOf("animego.ru", "animego.org", "animego.me", "animego.la")),
        p("netflix", "Netflix", "Netflix", "media", listOf("netflix.com", "nflxvideo.net", "nflximg.net", "nflxso.net")),
        p("spotify", "Spotify", "Spotify", "media", listOf("spotify.com", "scdn.co", "spotifycdn.com")),
        p("cinema_more", "Другие кинотеатры и аниме", "Other movies & anime", "media", listOf("filmix.fm", "lostfilm.tv", "seasonvar.ru", "kinobase.org", "jut.su", "anilibria.tv", "animevost.org", "shikimori.one", "crunchyroll.com")),
        p("steam", "Steam", "Steam", "gaming", listOf("steampowered.com", "steamcommunity.com", "steamstatic.com", "steamusercontent.com")),
        p("roblox", "Roblox", "Roblox", "gaming", listOf("roblox.com")),
        p("itch_io", "Itch.io", "Itch.io", "gaming", listOf("itch.io", "html.itch.zone", "static.itch.io")),
        p("epic_games", "Epic Games Store", "Epic Games Store", "gaming", listOf("epicgames.com", "launcher.store.epicgames.com", "cdn1.unrealengine.com")),
        p("battle_net", "Battle.net", "Battle.net", "gaming", listOf("battle.net", "blizzard.com", "bnetcmsus-a.akamaihd.net")),
        p("openai", "ChatGPT", "ChatGPT", "ai", listOf("openai.com", "chatgpt.com", "oaistatic.com", "oaiusercontent.com")),
        p("google", "Google (поиск, почта, Play)", "Google (search, mail, Play)", "work", listOf("google.com", "gstatic.com", "googleapis.com", "gmail.com", "googleusercontent.com")),
        p("github", "GitHub", "GitHub", "work", listOf("github.com", "githubusercontent.com", "raw.githubusercontent.com")),
        p("proton", "Proton", "Proton", "work", listOf("proton.me", "protonmail.com", "pm.me")),
        p("notion", "Notion", "Notion", "work", listOf("notion.so", "notionusercontent.com")),
        p("zoom", "Zoom", "Zoom", "work", listOf("zoom.us", "zoomgov.com", "zoom.com")),
        p("microsoft", "Microsoft / Outlook", "Microsoft / Outlook", "work", listOf("microsoft.com", "live.com", "outlook.com", "office.com", "office365.com")),
        p("cloudflare", "Cloudflare (сайты за ним)", "Cloudflare (sites behind it)", "infra", listOf("cloudflare.com", "one.one.one.one")),
        p("bestchange", "BestChange", "BestChange", "other", listOf("bestchange.ru", "bestchange.com", "www.bestchange.ru")),
        p("control4", "Control4", "Control4", "other", emptyList()),
        p("misc_blocked", "SoundCloud, Medium, Patreon и др.", "SoundCloud, Medium, Patreon & more", "other", listOf("soundcloud.com", "medium.com", "patreon.com", "grammarly.com", "myanimelist.net")),
    ).sortedBy { CATEGORY_ORDER.indexOf(it.category) }

    val PRESETS_BY_KEY: Map<String, PresetMeta> = PRESETS.associateBy { it.key }

    // ── CIDR-механика ────────────────────────────────────────────────────────────

    class Cidr(val network: Long, val prefix: Int) {
        private val mask: Long get() = if (prefix == 0) 0L else (-1L shl (32 - prefix)) and 0xffffffffL
        val lastHost: Long get() = network + hostCount - 1
        val hostCount: Long
            get() = if (prefix == 0) 1L shl 32 else (1L shl (32 - prefix))

        fun contains(other: Cidr): Boolean =
            other.network >= network && other.lastHost <= lastHost

        override fun toString(): String = "${networkToString()}/$prefix"

        fun networkToString(): String =
            "${(network ushr 24 and 0xff).toInt()}.${(network ushr 16 and 0xff).toInt()}.${(network ushr 8 and 0xff).toInt()}.${(network and 0xff).toInt()}"
    }

    fun parseCidr(s: String): Cidr? {
        val parts = s.trim().split("/")
        if (parts.size != 2) return null
        val ipParts = parts[0].split(".")
        if (ipParts.size != 4) return null
        var addr = 0L
        for (p in ipParts) {
            val v = p.toIntOrNull() ?: return null
            if (v !in 0..255) return null
            addr = (addr shl 8) or v.toLong()
        }
        val prefix = parts[1].toIntOrNull() ?: return null
        if (prefix !in 0..32) return null
        val cidr = Cidr(addr and 0xffffffffL, prefix)
        return Cidr(cidr.network, prefix)
    }

    /** Вычитание cut из from: результат — до двух подсетей в out (рекурсивный сплит). */
    private fun subtractInto(from: Cidr, cut: Cidr, out: MutableList<Cidr>) {
        if (from.prefix < cut.prefix) {
            val low = Cidr(from.network, from.prefix + 1)
            val high = Cidr(from.network + (from.hostCount ushr 1), from.prefix + 1)
            subtractInto(low, cut, out)
            subtractInto(high, cut, out)
            return
        }
        // from.prefix >= cut.prefix: from не крупнее cut
        if (from.contains(cut) || from.network == cut.network) {
            return // from целиком внутри cut → выкинуть
        }
        out.add(from)
    }

    /** Агрегация: сортировка, удаление покрытых, склейка соседних. */
    fun mergeCidrs(cidrs: List<Cidr>): List<Cidr> {
        if (cidrs.isEmpty()) return emptyList()
        val sorted = cidrs.map { Cidr(it.network, it.prefix) }
            .sortedWith(compareBy({ it.network }, { it.prefix }))
        val deduped = mutableListOf<Cidr>()
        for (cidr in sorted) {
            val last = deduped.lastOrNull()
            if (last == null) {
                deduped.add(cidr)
            } else if (!last.contains(cidr)) {
                deduped.add(cidr)
            }
        }
        // sibling merge: два соседних одинаковых префикса → родитель
        val merged = mutableListOf<Cidr>()
        var i = 0
        while (i < deduped.size) {
            var current = deduped[i]
            var again = true
            while (again) {
                again = false
                val last = merged.lastOrNull() ?: break
                if (last.prefix == current.prefix && last.prefix > 0) {
                    val parentMask = if (last.prefix - 1 == 0) 0L else (-1L shl (32 - (last.prefix - 1))) and 0xffffffffL
                    if (last.network and parentMask == current.network and parentMask) {
                        merged.removeAt(merged.size - 1)
                        current = Cidr(last.network and parentMask, last.prefix - 1)
                        again = true
                    }
                }
            }
            merged.add(current)
            i++
        }
        return merged
    }

    // ── Резолв ───────────────────────────────────────────────────────────────────

    const val LIMIT = 1000

    data class ResolvedRoutes(
        val cidrs: List<String>,
        val before4: Int,
        val after4: Int,
        val warnings: List<String>,
    )

    private data class Catalog(
        val routes: Map<String, Pair<List<Cidr>, List<String>>>,
        val warpEndpointCidrs24: List<Cidr>,
    )

    private var cache: Catalog? = null

    private fun readCatalog(stream: () -> InputStream): Catalog {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val json = stream().use { input -> JSONObject(input.readBytes().decodeToString()) }
            val presets = json.getJSONObject("presets")
            val routes = mutableMapOf<String, Pair<List<Cidr>, List<String>>>()
            for (key in presets.keys()) {
                val data = presets.getJSONObject(key)
                val v4 = data.optJSONArray("cidrs4") ?: continue
                val list4 = (0 until v4.length()).mapNotNull { parseCidr(v4.getString(it)) }
                val v6 = data.optJSONArray("cidrs6")
                val list6 = v6?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
                routes[key] = list4 to list6
            }
            val warp24 = json.optJSONArray("warpEndpointCidrs24")?.let { arr ->
                (0 until arr.length()).mapNotNull { parseCidr(arr.getString(it)) }
            } ?: emptyList()
            val catalog = Catalog(routes, warp24)
            cache = catalog
            return catalog
        }
    }

    /** Сброс кэша (тесты). */
    fun resetCache() {
        synchronized(this) { cache = null }
    }

    /**
     * Маршруты выбранных пресетов: агрегированные IPv4 (+IPv6 опционально),
     * минус подсети endpoint'ов WARP и минус адреса endpointOverride, лимит 1000 IPv4.
     */
    fun resolve(
        presetKeys: Collection<String>,
        stream: () -> InputStream,
        includeIpv6: Boolean = false,
        endpointOverride: String? = null,
        noLimit: Boolean = false,
    ): ResolvedRoutes {
        val catalog = readCatalog(stream)
        val normalized = presetKeys.map { raw ->
            val k = raw.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
            PRESET_ALIASES[k] ?: k
        }.filter { PRESETS_BY_KEY.containsKey(it) }.toSet()

        val all4 = mutableListOf<Cidr>()
        val all6 = mutableSetOf<String>()
        for (key in normalized) {
            catalog.routes[key]?.let { (v4, v6) ->
                all4.addAll(v4)
                if (includeIpv6) all6.addAll(v6)
            }
        }
        val before4 = all4.size
        var current = mergeCidrs(all4)

        // вычитание подсетей endpoint'ов WARP (I14/I15 донора)
        for (cut in catalog.warpEndpointCidrs24) {
            val next = mutableListOf<Cidr>()
            for (route in current) {
                if (route.network > cut.lastHost || route.lastHost < cut.network) {
                    next.add(route) // не пересекаются
                } else {
                    subtractInto(route, cut, next)
                }
            }
            current = mergeCidrs(next)
        }
        // endpoint override внутри маршрутов: вырезать его /32 (I14)
        if (endpointOverride != null) {
            val cut = parseCidr("$endpointOverride/32")
            if (cut != null) {
                val next = mutableListOf<Cidr>()
                for (route in current) {
                    if (route.network > cut.lastHost || route.lastHost < cut.network) next.add(route)
                    else subtractInto(route, cut, next)
                }
                current = mergeCidrs(next)
            }
        }

        val warnings = mutableListOf<String>()
        var limited = current
        if (!noLimit && current.size > LIMIT) {
            limited = current.subList(0, LIMIT)
            warnings.add(
                "Маршрутов больше лимита: оставлены первые $LIMIT IPv4 CIDR (всего ${current.size}). " +
                    "Включите «Без лимита», если клиент потянет."
            )
        }
        val v4Strings = limited.map { it.toString() }
        val cidrs = if (includeIpv6) v4Strings + all6.sorted() else v4Strings
        return ResolvedRoutes(cidrs, before4, limited.size, warnings)
    }

    /** Matcher: покрыт ли адрес любым CIDR из списка (для выбора endpoint вне маршрутов). */
    fun routeMatcher(cidrs: List<String>): (String) -> Boolean {
        val nets = cidrs.mapNotNull { parseCidr(it) }.sortedBy { it.network }
        if (nets.isEmpty()) return { _ -> false }
        fun match(ip: String): Boolean {
            val probe = parseCidr("$ip/32") ?: return false
            var lo = 0
            var hi = nets.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                val net = nets[mid]
                when {
                    probe.network < net.network -> hi = mid - 1
                    probe.network > net.lastHost -> lo = mid + 1
                    else -> return true
                }
            }
            return false
        }
        return { ip -> match(ip) }
    }
}
