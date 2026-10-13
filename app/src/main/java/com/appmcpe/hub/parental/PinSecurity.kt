package com.appmcpe.hub.parental

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** El PIN nunca se guarda: solo un hash con sal aleatoria (PBKDF2). */
object PinHasher {
    private const val ITERATIONS = 60_000
    private const val KEY_BITS = 256
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let(encoder::encodeToString)

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), decoder.decode(salt), ITERATIONS, KEY_BITS)
        return encoder.encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
    }

    fun matches(pin: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expectedHash.toByteArray())

    fun isValidFormat(pin: String) = pin.length == 4 && pin.all { it.isDigit() }
}

/** Bloqueo temporal tras demasiados PIN incorrectos, para que no se pueda adivinar probando. */
data class PinAttempts(val failures: Int = 0, val lockedUntil: Long = 0L) {
    fun lockRemainingMs(now: Long): Long = (lockedUntil - now).coerceAtLeast(0)

    fun onFailure(now: Long): PinAttempts {
        val n = failures + 1
        return if (n >= MAX_FAILURES) PinAttempts(0, now + LOCK_MS) else PinAttempts(n, lockedUntil)
    }

    val remainingTries: Int get() = MAX_FAILURES - failures

    companion object {
        const val MAX_FAILURES = 5
        const val LOCK_MS = 60_000L
    }
}

sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val remainingTries: Int) : PinResult
    data class Locked(val seconds: Int) : PinResult
}
