package dev.dhnam.screentranslator

import java.io.File
import java.io.OutputStream

/** 캡처 PNG를 앱 캐시에 저장하고, 오래된 캡처를 정리한다. */
class ShotStore(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** `write`로 내용을 채운 새 캡처 파일을 만든다. 실패하면 파일을 남기지 않고 예외를 다시 던진다. */
    fun save(write: (OutputStream) -> Unit): File {
        dir.mkdirs()
        val millis = clock()
        var file = File(dir, "shot-$millis.png")
        var n = 1
        while (!file.createNewFile()) {
            file = File(dir, "shot-$millis-$n.png")
            n++
        }
        try {
            file.outputStream().use(write)
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        return file
    }

    /** 마지막 수정 시각이 `maxAgeMs`보다 오래된 캡처를 지우고, 지운 개수를 돌려준다. */
    fun purgeOlderThan(maxAgeMs: Long): Int {
        val cutoff = clock() - maxAgeMs
        val shots = dir.listFiles { f -> f.isFile && f.name.startsWith("shot-") && f.name.endsWith(".png") }
            ?: return 0
        return shots.count { it.lastModified() < cutoff && it.delete() }
    }

    companion object {
        const val MAX_AGE_MS = 10 * 60 * 1000L
    }
}
