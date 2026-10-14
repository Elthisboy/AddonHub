package com.appmcpe.hub.install

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.appmcpe.hub.text.UiText
import com.appmcpe.hub.text.toUiText
import com.appmcpe.hub.user.UserLists
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Estado de la pantalla de descarga. */
sealed interface Stage {
    /** Buscando qué más necesita el contenido. */
    data object Preparing : Stage

    /** Descargando el archivo [index] de [count]. [total] es -1 si el servidor no informa el tamaño. */
    data class Downloading(
        val index: Int,
        val count: Int,
        val name: String,
        val done: Long,
        val total: Long,
    ) : Stage {
        val progress: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
    }

    /** Todo descargado mientras la app estaba en segundo plano: falta tocar "Importar". */
    data class ReadyToImport(val count: Int) : Stage

    data class Opening(val name: String) : Stage

    /** Se envió [sentName] a Minecraft; falta importar [nextName] y los siguientes. */
    data class NextStep(val sent: Int, val count: Int, val sentName: String, val nextName: String) : Stage
    data class Done(val count: Int) : Stage
    data class Error(val text: UiText) : Stage
}

/** Verdadero mientras hay trabajo en curso (el servicio de descarga debe seguir vivo). */
val Stage.isWorking: Boolean
    get() = this is Stage.Preparing || this is Stage.Downloading || this is Stage.Opening

/** Lo que se está instalando; mientras exista se muestra la pantalla de descarga. */
data class InstallSession(
    val title: String,
    val logoUrl: String?,
    val stage: Stage,
    /** Nombres de todo lo que se instalará, en orden. */
    val plan: List<String> = emptyList(),
    /** Avisos (dependencias que no se pueden bajar, APIs beta, versión de Minecraft…). */
    val notes: List<UiText> = emptyList(),
    val retry: (() -> Unit)? = null,
)

/** Un archivo por descargar. [fetch] recibe un callback de progreso (bytes, total). */
class InstallItem(
    val title: String,
    /** Id del contenido en CurseForge, si viene del catálogo (para "Mis descargas"). */
    val modId: Int? = null,
    val fetch: suspend ((Long, Long) -> Unit) -> File,
)

private class PendingFile(val title: String, val file: File, val modId: Int?)

class InstallSpec(val items: List<InstallItem>, val notes: List<UiText> = emptyList())

/**
 * Dueño de la instalación en curso. Vive a nivel de aplicación (no de pantalla) para que la
 * descarga siga cuando el usuario sale de la app; [DownloadService] la mantiene viva y avisa.
 */
object InstallManager {
    private lateinit var app: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _session = MutableStateFlow<InstallSession?>(null)
    val session: StateFlow<InstallSession?> = _session

    /** Lo actualiza MainActivity (onStart/onStop). Decide si se abre Minecraft solo o se avisa. */
    @Volatile
    var appInForeground = false

    private var job: Job? = null

    /** Archivos ya descargados esperando su turno de importar, en orden. */
    private var pending: List<PendingFile> = emptyList()
    private var sentIndex = -1

    fun init(application: Application) {
        app = application
    }

    private fun setStage(stage: Stage) = _session.update { it?.copy(stage = stage) }

    fun start(title: String, logoUrl: String?, retry: () -> Unit, prepare: suspend () -> InstallSpec) {
        if (_session.value != null) return
        _session.value = InstallSession(title, logoUrl, Stage.Preparing, retry = retry)
        sentIndex = -1
        pending = emptyList()
        startService()

        job = scope.launch {
            try {
                McInstaller.clearImports(app)
                val spec = prepare()
                _session.update { it?.copy(plan = spec.items.map { i -> i.title }, notes = spec.notes) }

                val files = ArrayList<File>()
                spec.items.forEachIndexed { i, item ->
                    setStage(Stage.Downloading(i, spec.items.size, item.title, 0, -1))
                    files += item.fetch { done, total ->
                        setStage(Stage.Downloading(i, spec.items.size, item.title, done, total))
                    }
                }

                // Revisar los manifest.json por si piden algo más.
                val found = withContext(Dispatchers.Default) {
                    PackInspector.inspect(files, McInstaller.installedVersion(app))
                }
                _session.update { it?.copy(notes = (it.notes + found).distinct()) }

                pending = spec.items.zip(files) { item, file -> PendingFile(item.title, file, item.modId) }
                // Android no deja abrir otra app desde el fondo: si el usuario salió, se le avisa.
                if (appInForeground) importAt(0) else setStage(Stage.ReadyToImport(pending.size))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStage(Stage.Error(e.toUiText()))
            }
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        } catch (_: Exception) {
            // Sin servicio la descarga igual avanza mientras el proceso siga vivo.
        }
    }

    private fun importAt(index: Int) {
        val current = pending[index]
        val name = current.title
        setStage(Stage.Opening(name))
        McInstaller.install(app, current.file).fold(
            onSuccess = {
                sentIndex = index
                current.modId?.let { UserLists.addHistory(it) } // queda en "Mis descargas"
                setStage(
                    if (index == pending.lastIndex) Stage.Done(pending.size)
                    else Stage.NextStep(index, pending.size, name, pending[index + 1].title),
                )
            },
            onFailure = { setStage(Stage.Error(it.toUiText())) },
        )
    }

    /** Importa el siguiente archivo (el primero tras "descargado", los demás tras cada paso). */
    fun importNext() {
        if (sentIndex + 1 in pending.indices) importAt(sentIndex + 1)
    }

    /** Cancela la descarga en curso o cierra la pantalla (si ya terminó o falló). */
    fun dismiss() {
        job?.cancel()
        pending = emptyList()
        _session.value = null
    }

    fun retry() {
        val retry = _session.value?.retry ?: return
        dismiss()
        retry()
    }
}
