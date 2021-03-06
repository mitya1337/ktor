package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.time.*
import java.time.temporal.*

class SessionTransportCookie(val name: String,
                             val configuration: CookieConfiguration,
                             val transformers: List<SessionTransportTransformer>
) : SessionTransport {

    override fun receive(call: ApplicationCall): String? {
        return transformers.transformRead(call.request.cookies[name])
    }

    override fun send(call: ApplicationCall, value: String) {
        val now = LocalDateTime.now()
        val expires = now.plus(configuration.duration)
        val maxAge = configuration.duration[ChronoUnit.SECONDS].toInt()
        val cookie = Cookie(name,
                transformers.transformWrite(value),
                configuration.encoding,
                maxAge,
                expires,
                configuration.domain,
                configuration.path,
                configuration.secure,
                configuration.httpOnly,
                configuration.extensions
        )

        call.response.cookies.append(cookie)
    }

    override fun clear(call: ApplicationCall) {
        call.response.cookies.appendExpired(name)
    }
}

class CookieConfiguration {
    var duration: TemporalAmount = Duration.ofDays(7)
    var encoding: CookieEncoding = CookieEncoding.URI_ENCODING
    var domain: String? = null
    var path: String? = null
    var secure: Boolean = false
    var httpOnly: Boolean = false
    val extensions: MutableMap<String, String?> = mutableMapOf()
}
