package app.proyecta.desktop

import app.proyecta.shared.ClientMessage
import kotlinx.coroutines.flow.StateFlow

/**
 * Clave para controlar desde la red. Sin la clave un dispositivo puede mirar (vista de escenario,
 * OBS, buscar en la Biblia) pero no cambiar lo que se proyecta. Tras [MAX_TRIES] intentos fallidos
 * desde una misma dirección, esa dirección queda bloqueada [LOCK_MILLIS].
 */
class PinGuard(
    private val pin: StateFlow<String?>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface Result {
        data object Ok : Result
        data class Wrong(val triesLeft: Int) : Result
        data class Locked(val seconds: Long) : Result
    }

    private class Failures(var count: Int, var lockedUntil: Long)
    private val failures = HashMap<String, Failures>()

    val required: Boolean get() = !pin.value.isNullOrEmpty()

    /** ¿Una sesión que dio [granted] puede controlar ahora? (si la clave cambió, ya no) */
    fun allows(granted: String?): Boolean = !required || granted == pin.value

    @Synchronized
    fun check(address: String, attempt: String): Result {
        val now = clock()
        val f = failures.getOrPut(address) { Failures(0, 0) }
        if (f.lockedUntil > now) return Result.Locked((f.lockedUntil - now + 999) / 1000)
        if (!required || attempt.trim() == pin.value) {
            failures.remove(address)
            return Result.Ok
        }
        f.count++
        if (f.count >= MAX_TRIES) {
            f.count = 0
            f.lockedUntil = now + LOCK_MILLIS
            return Result.Locked(LOCK_MILLIS / 1000)
        }
        return Result.Wrong(MAX_TRIES - f.count)
    }

    companion object {
        const val MAX_TRIES = 5
        const val LOCK_MILLIS = 60_000L

        /** Lo que se puede pedir sin clave: solo mirar o consultar. */
        fun isReadOnly(msg: ClientMessage) =
            msg is ClientMessage.BibleSearch || msg is ClientMessage.BibleChapter || msg is ClientMessage.Login
    }
}
