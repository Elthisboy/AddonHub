package com.appmcpe.hub.download

import com.appmcpe.hub.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.net.URLEncoder

/**
 * De dónde sale la información: con proxy ([proxyUrl] configurado) todo pasa por nuestro servidor,
 * que es el único que tiene la API key. Sin proxy (solo desarrollo) se habla directo con CurseForge.
 */
object Backend {
    private const val DIRECT_API = "https://api.curseforge.com/v1"

    val proxyUrl: String get() = BuildConfig.CF_PROXY_URL.trimEnd('/')
    val usesProxy: Boolean get() = proxyUrl.isNotBlank()

    /** Hay con qué consultar el catálogo (proxy o key directa). */
    val configured: Boolean get() = usesProxy || BuildConfig.CF_API_KEY.isNotBlank()

    val apiBase: String get() = apiBase(proxyUrl)

    fun apiBase(proxy: String): String = if (proxy.isBlank()) DIRECT_API else "${proxy.trimEnd('/')}/v1"

    /** Añade a la petición la credencial que corresponda (token de la app o key directa). */
    fun authorize(builder: Request.Builder): Request.Builder = builder.apply {
        if (usesProxy) header("X-App-Token", BuildConfig.CF_APP_TOKEN)
        else if (BuildConfig.CF_API_KEY.isNotBlank()) header("x-api-key", BuildConfig.CF_API_KEY)
    }

    /**
     * Prepara una descarga. Con proxy, los archivos del CDN de CurseForge se piden por `/dl`;
     * sin proxy van directo y la key solo se envía a los dominios del propio CDN.
     */
    fun download(url: String): Request.Builder {
        val builder = Request.Builder().header("User-Agent", Http.USER_AGENT)
        val host = url.toHttpUrlOrNull()?.host.orEmpty()
        if (isCdnHost(host)) {
            if (usesProxy) {
                return builder.url(proxyDownloadUrl(proxyUrl, url)).also { authorize(it) }
            }
            if (BuildConfig.CF_API_KEY.isNotBlank()) builder.header("x-api-key", BuildConfig.CF_API_KEY)
        }
        return builder.url(url)
    }

    fun isCdnHost(host: String) = host == "forgecdn.net" || host.endsWith(".forgecdn.net")

    fun proxyDownloadUrl(proxy: String, cdnUrl: String): String =
        "${proxy.trimEnd('/')}/dl?u=${URLEncoder.encode(cdnUrl, "UTF-8")}"
}
