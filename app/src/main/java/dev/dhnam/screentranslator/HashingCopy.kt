package dev.dhnam.screentranslator

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** 스트림을 복사하면서 SHA-256과 진행률을 계산한다. */
object HashingCopy {
    /** 복사한 바이트의 SHA-256(소문자 hex)을 돌려준다. 입출력 예외는 그대로 던진다. */
    fun copy(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            md.update(buf, 0, n)
            total += n
            onProgress(total)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** 기대값이 없으면 true(서명 확인만 믿음), 있으면 대소문자 무시 비교. */
    fun hashesMatch(expected: String?, actual: String): Boolean =
        expected == null || expected.equals(actual, ignoreCase = true)
}
