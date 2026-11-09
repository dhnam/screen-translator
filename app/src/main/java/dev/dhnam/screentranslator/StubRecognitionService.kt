package dev.dhnam.screentranslator

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** 어시스턴트 등록에 필수인 음성 인식 껍데기. 호출되면 바로 오류를 돌려준다. */
class StubRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
    }
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
