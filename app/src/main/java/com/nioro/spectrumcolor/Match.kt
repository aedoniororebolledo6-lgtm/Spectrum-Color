package com.nioro.spectrumcolor

/**
 * Reglas del multijugador por puntos. Los dos celulares juegan el mismo recorrido (misma semilla)
 * al mismo tiempo; cada estrella cuenta 1 punto. La ronda termina cuando los dos se eliminan o se
 * acaba el tiempo. Gana quien sume más puntos en total tras todas las rondas.
 *
 * Sin dependencias de Android: [send] manda una línea al rival.
 */
class Match(
    val isHost: Boolean,
    myName: String,
    private val send: (String) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    enum class Phase { LOBBY, COUNTDOWN, RUN, ROUND_END, MATCH_END, CLOSED }

    var phase = Phase.LOBBY
        private set
    val myName: String = NetUtil.clean(myName)
    var oppName = "Rival"
        private set
    var connected = false
        private set

    var rounds = 3
    var round = 0
        private set
    var seed = 0L
        private set

    var myLive = 0
        private set
    var oppLive = 0
        private set
    var myDone = false
        private set
    var oppDone = false
        private set
    var myTotal = 0
        private set
    var oppTotal = 0
        private set
    val myRounds = ArrayList<Int>()
    val oppRounds = ArrayList<Int>()

    var myRematch = false
        private set
    var oppRematch = false
        private set

    private var roundEndAt = 0L
    private var pendingMyFinal = 0
    private var pendingOppFinal = 0
    private val rnd = java.util.Random()

    companion object {
        const val NEXT_ROUND_DELAY_MS = 4500L
        val ROUND_OPTIONS = intArrayOf(1, 3, 5)

        /** Sube cuando cambia la forma de generar los recorridos: dos celulares con versiones distintas no se pueden emparejar. */
        const val PROTO = 3
    }

    /** Llamar al conectarse el socket. El invitado se presenta primero. */
    fun onConnected() {
        if (!isHost) send("HELLO|$myName|$PROTO")
    }

    /** true si el rival se fue (aunque la partida ya hubiera terminado). */
    var oppLeft = false
        private set

    /** true si la sala se cerró porque las dos apps son de versiones distintas. */
    var badVersion = false
        private set

    fun onClosed() {
        connected = false
        oppLeft = true
        // Si la partida ya terminó se queda el resultado en pantalla; solo se pierde la revancha.
        if (phase != Phase.MATCH_END) phase = Phase.CLOSED
    }

    fun leave() {
        send("BYE")
    }

    /** Solo el anfitrión: empieza la partida desde la ronda 1. */
    fun hostStart() {
        if (!isHost || !connected) return
        startRound(1)
    }

    private fun startRound(r: Int) {
        round = r
        if (r == 1) {
            myTotal = 0
            oppTotal = 0
            myRounds.clear()
            oppRounds.clear()
        }
        seed = (rnd.nextLong() and 0x7fffffffffffL)
        send("START|$r|$seed|$rounds")
        enterCountdown()
    }

    private fun enterCountdown() {
        phase = Phase.COUNTDOWN
        myLive = 0
        oppLive = 0
        myDone = false
        oppDone = false
        myRematch = false
        oppRematch = false
    }

    /** El juego avisa cuando termina la cuenta atrás. */
    fun beginRun() {
        if (phase == Phase.COUNTDOWN) phase = Phase.RUN
    }

    fun localLive(score: Int) {
        if (phase != Phase.RUN || score == myLive) return
        myLive = score
        send("SCORE|$score")
    }

    /** El jugador local fue eliminado o se acabó su tiempo. */
    fun localFinish(score: Int) {
        if (phase != Phase.RUN || myDone) return
        myLive = score
        myDone = true
        pendingMyFinal = score
        send("DEAD|$score")
        maybeRoundEnd()
    }

    fun rematch() {
        if (phase != Phase.MATCH_END || myRematch) return
        myRematch = true
        send("REMATCH")
        maybeRematch()
    }

    /** Llamar cada fotograma: el anfitrión lanza la siguiente ronda solo. */
    fun tick() {
        if (isHost && phase == Phase.ROUND_END && now() - roundEndAt >= NEXT_ROUND_DELAY_MS) {
            startRound(round + 1)
        }
    }

    fun onMessage(line: String) {
        val p = line.split("|")
        when (p[0]) {
            "HELLO" -> if (isHost) {
                if ((p.getOrNull(2)?.toIntOrNull() ?: 0) != PROTO) {
                    badVersion = true
                    send("BADVER|$PROTO")
                    onClosed()
                    return
                }
                oppName = NetUtil.clean(p.getOrElse(1) { "Rival" })
                connected = true
                send("WELCOME|$myName|$rounds|$PROTO")
            }
            "BADVER" -> {
                badVersion = true
                onClosed()
            }
            "WELCOME" -> if (!isHost) {
                if ((p.getOrNull(3)?.toIntOrNull() ?: 0) != PROTO) {
                    badVersion = true
                    send("BADVER|$PROTO")
                    onClosed()
                    return
                }
                oppName = NetUtil.clean(p.getOrElse(1) { "Anfitrión" })
                rounds = (p.getOrNull(2)?.toIntOrNull() ?: 3).coerceIn(1, 9)
                connected = true
            }
            "START" -> if (!isHost) {
                val r = p.getOrNull(1)?.toIntOrNull() ?: return
                val s = p.getOrNull(2)?.toLongOrNull() ?: return
                rounds = (p.getOrNull(3)?.toIntOrNull() ?: rounds).coerceIn(1, 9)
                round = r
                seed = s
                if (r == 1) {
                    myTotal = 0
                    oppTotal = 0
                    myRounds.clear()
                    oppRounds.clear()
                }
                enterCountdown()
            }
            "SCORE" -> oppLive = p.getOrNull(1)?.toIntOrNull() ?: oppLive
            "DEAD" -> {
                val sc = p.getOrNull(1)?.toIntOrNull() ?: oppLive
                oppLive = sc
                oppDone = true
                pendingOppFinal = sc
                maybeRoundEnd()
            }
            "REMATCH" -> {
                oppRematch = true
                maybeRematch()
            }
            "BYE" -> onClosed()
        }
    }

    private fun maybeRoundEnd() {
        if (phase != Phase.RUN || !myDone || !oppDone) return
        myRounds.add(pendingMyFinal)
        oppRounds.add(pendingOppFinal)
        myTotal += pendingMyFinal
        oppTotal += pendingOppFinal
        roundEndAt = now()
        phase = if (round >= rounds) Phase.MATCH_END else Phase.ROUND_END
    }

    private fun maybeRematch() {
        if (isHost && myRematch && oppRematch && phase == Phase.MATCH_END) startRound(1)
    }

    /** 1 = gané, 0 = empate, -1 = perdí (solo tiene sentido en MATCH_END). */
    fun result(): Int = when {
        myTotal > oppTotal -> 1
        myTotal < oppTotal -> -1
        else -> 0
    }
}
