package com.fnmusic.tv.core.data.api

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

/**
 * Accepts hostname mismatches only when the target host is a raw IP literal
 * (NAS home-LAN access with a domain-issued certificate); every other host —
 * domains included — keeps strict verification through the platform verifier.
 * The certificate chain itself must still be system-trusted.
 */
class NasHostnameVerifier(
    private val fallback: HostnameVerifier,
) : HostnameVerifier {
    override fun verify(hostname: String, session: SSLSession): Boolean =
        isIpLiteralHost(hostname) || fallback.verify(hostname, session)

    companion object {
        fun isIpLiteralHost(host: String): Boolean =
            host.removeSurrounding("[", "]").let { bare ->
                bare.contains(":") || Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(bare)
            }
    }
}
