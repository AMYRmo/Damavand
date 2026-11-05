package com.amyrmo.damavand.platform

import com.amyrmo.damavand.domain.config.LocalProxyAuth
import java.net.Authenticator
import java.net.PasswordAuthentication

private val LOOPBACK = setOf("127.0.0.1", "localhost", "::1", "0:0:0:0:0:0:0:1")

@Volatile private var installed = false

internal actual fun installLocalProxyAuthenticator() {
    if (installed) return
    synchronized(LOOPBACK) {
        if (installed) return
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val credentials = LocalProxyAuth.current ?: return null
                if (requestingProtocol?.startsWith("SOCKS", ignoreCase = true) != true) return null
                val host = requestingHost ?: requestingSite?.hostAddress
                if (host != null && host !in LOOPBACK) return null
                return PasswordAuthentication(credentials.user, credentials.pass.toCharArray())
            }
        })
        installed = true
    }
}
