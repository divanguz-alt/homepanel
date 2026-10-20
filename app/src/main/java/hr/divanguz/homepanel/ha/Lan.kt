package hr.divanguz.homepanel.ha

/** Which hosts may be reached over plain http: the home network (and Tailscale), never the internet. */
object Lan {
    fun isLocal(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa") || h.endsWith(".internal")) return true
        if (!h.contains('.') && !h.contains(':')) return true // single-label names like "homeassistant"
        if (h.startsWith("fe80:") || h.startsWith("fd") || h.startsWith("fc")) return true // IPv6 link-local / ULA
        val p = h.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 ||
            p[0] == 127 ||
            (p[0] == 192 && p[1] == 168) ||
            (p[0] == 172 && p[1] in 16..31) ||
            (p[0] == 169 && p[1] == 254) ||
            (p[0] == 100 && p[1] in 64..127) // Tailscale / CGNAT
    }
}
