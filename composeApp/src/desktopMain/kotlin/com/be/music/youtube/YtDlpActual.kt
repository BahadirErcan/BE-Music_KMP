package com.be.music.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Masaüstü yt-dlp runner: birlikte dağıtılan binary.
 * - Dizin arama: paketle gelen `yt-dlp` ("bin/yt-dlp(.exe)") veya sistem PATH'i.
 * - Linux/macOS: exec permission ayarlanır; Windows: yt-dlp.exe.
 */
actual object YtDlp {
    actual val supported: Boolean = true

    private var binaryFile: File? = null
    private var initialized = false

    private val processes = java.util.Collections.synchronizedList(mutableListOf<Process>())

    actual suspend fun init() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        initialized = true
        val resName = if (isWindows) "bin/yt-dlp.exe" else "bin/yt-dlp"
        val candidates = mutableListOf<File>()
        // 1) Açık dizinlerde binary var mı (geliştirme / manuel yerleşim)
        candidates.add(File(System.getProperty("user.dir"), resName))
        System.getProperty("compose.application.resources.dir")?.let { dir ->
            candidates.add(File(dir, resName))
            candidates.add(File(dir, "yt-dlp.exe"))
            candidates.add(File(dir, "yt-dlp"))
        }
        // 2) Jar/classpath kaynağı -> kullanıcı dizinine çıkar
        val extractedFile = File(System.getProperty("user.home"), ".be-music/bin/${if (isWindows) "yt-dlp.exe" else "yt-dlp"}")
        candidates.add(extractedFile)
        binaryFile = candidates.firstOrNull { it.isFile && it.length() > 0 }
        if (binaryFile == null) {
            val res = ClassLoader.getSystemResourceAsStream(resName)
            if (res != null) {
                try {
                    extractedFile.parentFile.mkdirs()
                    res.use { input -> extractedFile.outputStream().use { input.copyTo(it) } }
                    if (!isWindows) {
                        try { extractedFile.setExecutable(true) } catch (_: Exception) {}
                    }
                    binaryFile = extractedFile
                } catch (e: Exception) {
                    println("[YtDlp] Kaynaktan çıkarma başarısız: $e")
                }
            }
        }
        binaryFile?.let {
            if (!isWindows) {
                try { it.setExecutable(true) } catch (_: Exception) {}
            }
        }
        if (binaryFile == null && which("yt-dlp") == null && which("yt-dlp.exe") == null) {
            println("[YtDlp] Masaüstünde yt-dlp bulunamadı; sistem PATH'ine bakılacak")
        }
    }

    private val isWindows: Boolean get() = System.getProperty("os.name").lowercase().contains("win")

    private fun which(name: String): String? = try {
        val dirs = (System.getenv("PATH") ?: "").split(File.pathSeparator)
        dirs.map { File(it, name) }.firstOrNull { it.canExecute() || it.exists() }?.absolutePath
    } catch (_: Exception) { null }

    private fun binaryPath(): String? = binaryFile?.absolutePath ?: which("yt-dlp") ?: which("yt-dlp.exe")

    /** options: flag->value (boş value = sadece flag). Dönen: başarı durumu. */
    actual suspend fun run(
        options: List<Pair<String, String>>,
        onProgress: (percent: Float, etaSeconds: Long, line: String?) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        init()
        val args = buildArgs(options, addJsonDump = false)
        val path = binaryPath() ?: return@withContext Result.failure(IllegalStateException("yt-dlp binary bulunamadı"))
        try {
            val proc = ProcessBuilder(listOf(path) + args).start()
            val readers = listOf(
                proc.inputStream.bufferedReader(),
                proc.errorStream.bufferedReader()
            )
            val threads = readers.map { r ->
                Thread {
                    try {
                        r.forEachLine { line ->
                            val pct = parseProgress(line)
                            if (pct >= 0) onProgress(pct, 0L, line)
                        }
                    } catch (_: Exception) {}
                }.also { it.isDaemon = true; it.start() }
            }
            processes.add(proc)
            val code = proc.waitFor()
            processes.remove(proc)
            if (code == 0) Result.success(Unit)
            else Result.failure(RuntimeException("yt-dlp exit code $code"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseProgress(line: String): Float {
        // örnek: [download]  33.2% of 3.21MiB at ...
        return try {
            if (line.startsWith("[download]")) {
                Regex("(\\d+(?:\\.\\d+)?)%").find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
            } else -1f
        } catch (_: Exception) { -1f }
    }

    private fun buildArgs(options: List<Pair<String, String>>, addJsonDump: Boolean): List<String> {
        val args = mutableListOf<String>()
        if (addJsonDump) args.add("--dump-json")
        for ((flag, value) in options) {
            when {
                flag.isEmpty() -> { /* URL */ }
                value.isEmpty() -> args.add(flag)
                else -> { args.add(flag); args.add(value) }
            }
        }
        return args
    }

    actual suspend fun dumpJson(url: String): String? = withContext(Dispatchers.IO) {
        init()
        val path = binaryPath() ?: return@withContext null
        try {
            val proc = ProcessBuilder(listOf(path, "--dump-json", "--user-agent", defaultUserAgent(), url)).start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (proc.exitValue() == 0 && output.isNotBlank()) output.lines().firstOrNull() else null
        } catch (e: Exception) {
            println("[YtDlp] dumpJson hatası: $e")
            null
        }
    }

    actual fun cancelAll() {
        synchronized(processes) {
            processes.forEach { p -> try { p.destroy() } catch (_: Exception) {} }
            processes.clear()
        }
    }
}
