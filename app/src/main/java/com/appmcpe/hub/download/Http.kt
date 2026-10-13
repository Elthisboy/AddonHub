package com.appmcpe.hub.download

import com.appmcpe.hub.BuildConfig
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.IOException
import java.net.Inet6Address
import java.util.concurrent.TimeUnit

/** Clientes HTTP compartidos, pensados para redes móviles problemáticas. */
object Http {
    /** Prefiere IPv4: algunas redes móviles resetean la conexión por IPv6. */
    private val ipv4First = object : Dns {
        override fun lookup(hostname: String) =
            Dns.SYSTEM.lookup(hostname).sortedBy { it is Inet6Address }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(ipv4First)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Variante solo HTTP/1.1, por si HTTP/2 falla en la red del usuario. */
    val http11: OkHttpClient = client.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    const val USER_AGENT = "AddonHub/" + BuildConfig.VERSION_NAME

    /** Abre la respuesta (el llamador debe cerrarla); reintenta con HTTP/1.1 si falla la red. */
    fun open(request: Request): okhttp3.Response = try {
        client.newCall(request).execute()
    } catch (e: IOException) {
        http11.newCall(request).execute()
    }

    /** Ejecuta [request] con el cliente normal y, si hay un error de red, reintenta con HTTP/1.1. */
    fun <T> execute(request: Request, handle: (okhttp3.Response) -> T): T {
        return try {
            client.newCall(request).execute().use(handle)
        } catch (e: IOException) {
            if (e is com.appmcpe.hub.catalog.CurseForgeException) throw e
            http11.newCall(request).execute().use(handle)
        }
    }

    /** Prueba una URL y devuelve una línea legible con el resultado (para diagnóstico). */
    fun probe(label: String, url: String, withKey: Boolean): String {
        val addrs = try {
            val host = java.net.URI(url).host
            Dns.SYSTEM.lookup(host).joinToString { if (it is Inet6Address) "v6" else "v4" }
        } catch (e: Exception) {
            "DNS falló: ${e.javaClass.simpleName}"
        }
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).apply {
            if (withKey) Backend.authorize(this)
        }.build()
        fun attempt(c: OkHttpClient, name: String): String? = try {
            c.newCall(req).execute().use { "✅ $label: HTTP ${it.code} ($name, $addrs)" }
        } catch (e: Exception) {
            null.also { lastError = "${e.javaClass.simpleName}: ${e.message}" }
        }
        return attempt(client, "h2") ?: attempt(http11, "http1.1")
            ?: "❌ $label: $lastError ($addrs)"
    }

    @Volatile
    private var lastError: String = ""
}
