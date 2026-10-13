package com.appmcpe.hub.parental

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Guarda la configuración de los padres en el teléfono. Solo hay un PIN, sin cuentas. */
object ParentalStore {
    private lateinit var prefs: SharedPreferences

    private val _settings = MutableStateFlow(ParentalSettings())
    val settings: StateFlow<ParentalSettings> = _settings

    fun init(context: Context) {
        prefs = context.getSharedPreferences("parental", Context.MODE_PRIVATE)
        _settings.value = read()
    }

    private fun read(): ParentalSettings {
        val allowed = HashMap<Int, String>()
        runCatching {
            val o = JSONObject(prefs.getString(K_ALLOWED, "{}") ?: "{}")
            o.keys().forEach { allowed[it.toInt()] = o.getString(it) }
        }
        return ParentalSettings(
            onboarded = prefs.getBoolean(K_ONBOARDED, false),
            ageGroup = runCatching { AgeGroup.valueOf(prefs.getString(K_AGE, null) ?: "") }
                .getOrDefault(AgeGroup.KIDS),
            customWords = prefs.getStringSet(K_WORDS, emptySet()).orEmpty().toSet(),
            allowed = allowed,
            toolsEnabled = prefs.getBoolean(K_TOOLS, false),
            adultVerified = prefs.getBoolean(K_ADULT_VERIFIED, false),
            adultMode = adultMode,
        )
    }

    /** Solo en memoria: al cerrar la app el modo adulto se apaga solo. */
    private var adultMode = false

    /** Un adulto declaró su mayoría de edad; se guarda solo el "sí", nunca la fecha. */
    fun markAdultVerified() = edit { putBoolean(K_ADULT_VERIFIED, true) }

    fun setAdultMode(on: Boolean) {
        if (on && !prefs.getBoolean(K_ADULT_VERIFIED, false)) return
        adultMode = on
        _settings.value = read()
    }

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _settings.value = read()
    }

    val hasPin: Boolean get() = prefs.contains(K_PIN_HASH)

    // ---------- PIN ----------

    fun setPin(pin: String) {
        require(PinHasher.isValidFormat(pin)) { "El PIN debe tener 4 números" }
        val salt = PinHasher.newSalt()
        edit {
            putString(K_PIN_SALT, salt)
            putString(K_PIN_HASH, PinHasher.hash(pin, salt))
            putInt(K_FAILURES, 0)
            putLong(K_LOCKED_UNTIL, 0)
        }
    }

    fun verifyPin(pin: String, now: Long = System.currentTimeMillis()): PinResult {
        var attempts = PinAttempts(prefs.getInt(K_FAILURES, 0), prefs.getLong(K_LOCKED_UNTIL, 0))
        val locked = attempts.lockRemainingMs(now)
        if (locked > 0) return PinResult.Locked(((locked + 999) / 1000).toInt())

        val salt = prefs.getString(K_PIN_SALT, null)
        val hash = prefs.getString(K_PIN_HASH, null)
        if (salt != null && hash != null && PinHasher.matches(pin, salt, hash)) {
            saveAttempts(PinAttempts())
            return PinResult.Ok
        }
        attempts = attempts.onFailure(now)
        saveAttempts(attempts)
        val remaining = attempts.lockRemainingMs(now)
        return if (remaining > 0) PinResult.Locked(((remaining + 999) / 1000).toInt())
        else PinResult.Wrong(attempts.remainingTries)
    }

    private fun saveAttempts(a: PinAttempts) {
        prefs.edit().putInt(K_FAILURES, a.failures).putLong(K_LOCKED_UNTIL, a.lockedUntil).apply()
    }

    // ---------- Ajustes ----------

    /** Termina la configuración inicial: perfil de edad + PIN. */
    fun completeOnboarding(group: AgeGroup, pin: String) {
        setPin(pin)
        edit {
            putString(K_AGE, group.name)
            putBoolean(K_ONBOARDED, true)
        }
    }

    fun setAgeGroup(group: AgeGroup) = edit { putString(K_AGE, group.name) }

    fun addWord(word: String) {
        val w = word.trim().lowercase()
        if (w.length < 2) return
        edit { putStringSet(K_WORDS, _settings.value.customWords + w) }
    }

    fun removeWord(word: String) = edit { putStringSet(K_WORDS, _settings.value.customWords - word) }

    fun allow(modId: Int, name: String) = writeAllowed(_settings.value.allowed + (modId to name))

    fun disallow(modId: Int) = writeAllowed(_settings.value.allowed - modId)

    private fun writeAllowed(map: Map<Int, String>) {
        val json = JSONObject().apply { map.forEach { (id, name) -> put(id.toString(), name) } }
        edit { putString(K_ALLOWED, json.toString()) }
    }

    fun setToolsEnabled(enabled: Boolean) = edit { putBoolean(K_TOOLS, enabled) }

    private const val K_ONBOARDED = "onboarded"
    private const val K_AGE = "age"
    private const val K_WORDS = "words"
    private const val K_ALLOWED = "allowed"
    private const val K_TOOLS = "tools"
    private const val K_ADULT_VERIFIED = "adult_verified"
    private const val K_PIN_HASH = "pin_hash"
    private const val K_PIN_SALT = "pin_salt"
    private const val K_FAILURES = "pin_failures"
    private const val K_LOCKED_UNTIL = "pin_locked_until"
}
