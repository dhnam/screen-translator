package dev.dhnam.screentranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 영어(기본)와 한국어 문구 파일의 이름 목록이 같은지 확인한다. 단위 테스트의 작업 폴더는 app/이다. */
class StringsParityTest {
    private fun names(path: String): Set<String> {
        val f = File(path)
        assertTrue("missing $path", f.exists())
        return Regex("""<string name="([^"]+)"""").findAll(f.readText()).map { it.groupValues[1] }.toSet()
    }

    @Test fun main_englishAndKorean_haveSameNames() =
        assertEquals(names("src/main/res/values/strings.xml"), names("src/main/res/values-ko/strings.xml"))

    @Test fun debug_englishAndKorean_haveSameNames() =
        assertEquals(names("src/debug/res/values/strings.xml"), names("src/debug/res/values-ko/strings.xml"))
}
