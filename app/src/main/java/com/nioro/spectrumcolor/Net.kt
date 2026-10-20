package com.nioro.spectrumcolor

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/*
 * Red local (Wi-Fi) sin dependencias de Android, así se puede probar en una JVM.
 *
 *  - El anfitrión abre un puerto TCP y avisa por UDP broadcast cada 0,7 s ("SPECTRUM1|nombre|puerto").
 *  - El invitado escucha esos avisos (o escribe la IP a mano) y se conecta por TCP.
 *  - Los mensajes son líneas de texto "TIPO|dato|dato".
 *
 * Todos los hilos de red dejan sus eventos en una cola que lee el hilo del juego:
 *   "!CONNECTED|ip", "!CLOSED", "!ERROR|texto", "!ROOM|nombre|ip|puerto" y los mensajes del rival.
 */

object NetCfg {
    const val TCP_PORT = 47474
    const val UDP_PORT = 47475
    const val MAGIC = "SPECTRUM1"
    const val MAX_LINE = 512
    const val HEARTBEAT_MS = 1500L
    const val TIMEOUT_MS = 9000L
}

object NetUtil {
    fun clean(name: String): String {
        val s = name.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim()
        return if (s.isEmpty()) "Jugador" else s.take(14)
    }

    /** IPv4 propias (para mostrarlas al anfitrión). */
    fun localIps(): List<String> {
        val out = ArrayList<String>()
        try {
            val en = NetworkInterface.getNetworkInterfaces() ?: return out
            for (ni in en) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    val a = ia.address
                    if (a is Inet4Address && !a.isLoopbackAddress) a.hostAddress?.let { out.add(it) }
                }
            }
        } catch (e: Exception) {
            // sin interfaces disponibles
        }
        return out
    }

    fun broadcastTargets(extra: List<String>): List<InetAddress> {
        val out = ArrayList<InetAddress>()
        try {
            out.add(InetAddress.getByName("255.255.255.255"))
        } catch (e: Exception) {
        }
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            if (en != null) {
                for (ni in en) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (ia in ni.interfaceAddresses) {
                        val b = ia.broadcast
                        if (b != null && !out.contains(b)) out.add(b)
                    }
                }
            }
        } catch (e: Exception) {
        }
        for (x in extra) {
            try {
                out.add(InetAddress.getByName(x))
            } catch (e: Exception) {
            }
        }
        return out
    }
}

/** Una conexión TCP con hilo lector, hilo escritor y latido para detectar caídas. */
class Conn(private val sock: Socket, private val events: ConcurrentLinkedQueue<String>) {
    private val out = LinkedBlockingQueue<String>()

    @Volatile
    var open = true
        private set

    @Volatile
    private var lastRx = System.currentTimeMillis()

    fun start() {
        try {
            sock.tcpNoDelay = true
            sock.keepAlive = true
        } catch (e: Exception) {
        }
        Thread({ readLoop() }, "net-read").apply { isDaemon = true; start() }
        Thread({ writeLoop() }, "net-write").apply { isDaemon = true; start() }
    }

    fun send(msg: String) {
        if (open) out.offer(msg)
    }

    fun close() {
        if (!open) return
        open = false
        out.offer("\u0000")
        try {
            sock.close()
        } catch (e: Exception) {
        }
    }

    private fun finish() {
        val was = open
        open = false
        try {
            sock.close()
        } catch (e: Exception) {
        }
        if (was) events.offer("!CLOSED")
    }

    private fun readLoop() {
        try {
            val input = sock.getInputStream()
            val buf = ByteArray(NetCfg.MAX_LINE)
            var len = 0
            while (open) {
                val b = input.read()
                if (b < 0) break
                lastRx = System.currentTimeMillis()
                if (b == '\n'.code) {
                    val line = String(buf, 0, len, Charsets.UTF_8)
                    len = 0
                    if (line.isNotEmpty() && line != "PING") events.offer(line)
                } else if (len < buf.size) {
                    buf[len++] = b.toByte()
                } else {
                    len = 0 // línea demasiado larga: se descarta
                }
            }
        } catch (e: Exception) {
            // conexión cerrada
        }
        finish()
    }

    private fun writeLoop() {
        try {
            val os = sock.getOutputStream()
            while (open) {
                val m = out.poll(NetCfg.HEARTBEAT_MS, TimeUnit.MILLISECONDS)
                if (m == "\u0000") break
                val line = m ?: "PING"
                os.write((line + "\n").toByteArray(Charsets.UTF_8))
                os.flush()
                if (System.currentTimeMillis() - lastRx > NetCfg.TIMEOUT_MS) throw IOException("sin respuesta")
            }
        } catch (e: Exception) {
            // conexión caída
        }
        finish()
    }
}

/** Anfitrión: abre la sala, la anuncia y acepta a un solo invitado. */
class NetHost(
    private val name: String,
    private val events: ConcurrentLinkedQueue<String>,
    private val extraTargets: List<String> = emptyList(),
    private val tcpPort: Int = NetCfg.TCP_PORT,
    private val udpPort: Int = NetCfg.UDP_PORT
) {
    @Volatile
    private var running = false
    private var server: ServerSocket? = null

    @Volatile
    var conn: Conn? = null
        private set

    val port: Int get() = server?.localPort ?: tcpPort

    /** Devuelve false si no se pudo abrir el puerto. */
    fun start(): Boolean {
        return try {
            val s = ServerSocket()
            s.reuseAddress = true
            s.bind(InetSocketAddress(tcpPort))
            server = s
            running = true
            Thread({ acceptLoop(s) }, "net-accept").apply { isDaemon = true; start() }
            Thread({ announceLoop() }, "net-announce").apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun acceptLoop(s: ServerSocket) {
        try {
            val c = s.accept()
            running = false // deja de anunciar
            try {
                s.close()
            } catch (e: Exception) {
            }
            val cn = Conn(c, events)
            conn = cn
            cn.start()
            events.offer("!CONNECTED|" + (c.inetAddress?.hostAddress ?: "?"))
        } catch (e: Exception) {
            if (running) events.offer("!ERROR|accept")
        }
    }

    private fun announceLoop() {
        var sock: DatagramSocket? = null
        try {
            sock = DatagramSocket()
            sock.broadcast = true
            val payload = (NetCfg.MAGIC + "|" + NetUtil.clean(name) + "|" + port).toByteArray(Charsets.UTF_8)
            while (running) {
                for (addr in NetUtil.broadcastTargets(extraTargets)) {
                    try {
                        sock.send(DatagramPacket(payload, payload.size, addr, udpPort))
                    } catch (e: Exception) {
                        // esa interfaz no permite broadcast
                    }
                }
                Thread.sleep(700)
            }
        } catch (e: Exception) {
        } finally {
            try {
                sock?.close()
            } catch (e: Exception) {
            }
        }
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (e: Exception) {
        }
        conn?.close()
    }
}

/** Escucha los avisos de salas en la red. */
class NetScanner(
    private val events: ConcurrentLinkedQueue<String>,
    private val udpPort: Int = NetCfg.UDP_PORT
) {
    @Volatile
    private var running = false
    private var sock: DatagramSocket? = null

    fun start(): Boolean {
        return try {
            val s = DatagramSocket(null)
            s.reuseAddress = true
            s.bind(InetSocketAddress(udpPort))
            s.soTimeout = 500
            sock = s
            running = true
            Thread({ loop(s) }, "net-scan").apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun loop(s: DatagramSocket) {
        val buf = ByteArray(256)
        while (running) {
            try {
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                val text = String(p.data, 0, p.length, Charsets.UTF_8)
                val parts = text.split("|")
                if (parts.size >= 3 && parts[0] == NetCfg.MAGIC) {
                    val ip = p.address?.hostAddress ?: continue
                    events.offer("!ROOM|" + NetUtil.clean(parts[1]) + "|" + ip + "|" + parts[2])
                }
            } catch (e: java.net.SocketTimeoutException) {
                // seguir escuchando
            } catch (e: Exception) {
                if (running) break
            }
        }
        try {
            s.close()
        } catch (e: Exception) {
        }
    }

    fun stop() {
        running = false
        try {
            sock?.close()
        } catch (e: Exception) {
        }
    }
}

/** Invitado: se conecta a una sala por IP. */
class NetGuest(private val events: ConcurrentLinkedQueue<String>) {
    @Volatile
    var conn: Conn? = null
        private set

    @Volatile
    private var cancelled = false

    fun connect(ip: String, port: Int = NetCfg.TCP_PORT) {
        cancelled = false
        Thread({
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, port), 4000)
                if (cancelled) {
                    s.close()
                    return@Thread
                }
                val c = Conn(s, events)
                conn = c
                c.start()
                events.offer("!CONNECTED|$ip")
            } catch (e: Exception) {
                if (!cancelled) events.offer("!ERROR|connect")
            }
        }, "net-connect").apply { isDaemon = true; start() }
    }

    fun stop() {
        cancelled = true
        conn?.close()
    }
}
