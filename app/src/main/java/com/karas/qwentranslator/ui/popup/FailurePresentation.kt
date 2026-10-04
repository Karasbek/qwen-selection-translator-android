package com.karas.qwentranslator.ui.popup

import com.karas.qwentranslator.core.*

/** Never renders exception messages, response bodies or stack traces. */
object FailurePresentation {
    fun message(failure: TranslationFailure): String = when (failure.kind) {
        FailureKind.AUTHENTICATION -> "${failure.provider ?: "AI сервис"} отклонил API key. Проверьте ключ в настройках."
        FailureKind.RATE_LIMIT -> "Достигнут лимит запросов. Повторите позже."
        FailureKind.TIMEOUT -> "Время ожидания истекло. Попробуйте ещё раз."
        FailureKind.NETWORK -> "Нет соединения с сервисом. Проверьте сеть."
        FailureKind.ACCESS_DENIED -> "Сервис ограничил доступ. Проверьте доступность из вашей сети."
        FailureKind.UNAVAILABLE -> "Сервис временно недоступен. Повторите позже."
        FailureKind.MALFORMED_RESPONSE -> "Сервис вернул пустой, неполный или некорректный ответ. Попробуйте меньший фрагмент."
        FailureKind.CONFIGURATION -> "Проверьте API key, модель и адрес сервиса в настройках."
        FailureKind.INPUT_TOO_LONG -> "Выделите меньший фрагмент: максимум 12 000 символов."
        FailureKind.NO_PROVIDER -> "Нет доступного сервиса для этого результата."
        FailureKind.UNKNOWN -> "Не удалось обработать текст. Попробуйте ещё раз."
    }
}
