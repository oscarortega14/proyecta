package app.proyecta.desktop

import app.proyecta.shared.DEFAULT_PORT
import app.proyecta.shared.DISCOVERY_PORT
import app.proyecta.shared.DISCOVERY_REQUEST
import app.proyecta.shared.DiscoveryReply
import app.proyecta.shared.ProtocolJson
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * Responde a los remotos que preguntan "¿dónde está Proyecta?" por la red local, para que la
 * tablet o el celular se conecten solos sin escribir la IP.
 */
class DiscoveryResponder(private val port: Int = DISCOVERY_PORT, private val wsPort: Int = DEFAULT_PORT) {
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    val name: String = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("Proyecta")

    /** Puerto UDP en el que quedó escuchando (útil si se pidió 0 = cualquiera libre). */
    val boundPort: Int get() = socket?.localPort ?: -1

    fun start() {
        // Ojo: dentro de apply{} "port" sería el puerto remoto del socket (-1); se usa el nuestro explícitamente.
        val listenPort = this.port
        val s = DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(listenPort)) }
        socket = s
        thread = Thread({
            val buf = ByteArray(256)
            val reply = ProtocolJson.encodeToString(DiscoveryReply.serializer(), DiscoveryReply(name, wsPort)).toByteArray()
            while (!s.isClosed) {
                val packet = DatagramPacket(buf, buf.size)
                runCatching { s.receive(packet) }.onFailure { return@Thread }
                if (String(packet.data, 0, packet.length).trim() == DISCOVERY_REQUEST) {
                    runCatching { s.send(DatagramPacket(reply, reply.size, packet.socketAddress)) }
                }
            }
        }, "proyecta-descubrimiento").apply { isDaemon = true; start() }
    }

    fun stop() { socket?.close() }
}

/**
 * Regla del Firewall de Windows para que tablets y celulares de la red local puedan conectarse.
 * Se crea con un clic: Windows muestra su ventana de permiso (UAC) una sola vez.
 */
object FirewallSetup {
    const val RULE = "Proyecta (control remoto)"
    val isWindows = System.getProperty("os.name").startsWith("Windows")

    /** true si ya existe la regla (consultar no necesita permisos de administrador). */
    fun ruleExists(): Boolean {
        if (!isWindows) return true
        return runCatching {
            val p = ProcessBuilder("netsh", "advfirewall", "firewall", "show", "rule", "name=$RULE")
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            // Deben estar las dos: TCP del control remoto y UDP del descubrimiento.
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0 && RULE in out &&
                Regex("""\b$DEFAULT_PORT\b""").containsMatchIn(out) && Regex("""\b$DISCOVERY_PORT\b""").containsMatchIn(out)
        }.getOrDefault(false)
    }

    /**
     * Órdenes que se ejecutan como administrador. Además de permitir los puertos de Proyecta
     * en la red local (cualquier tipo de red), borra las reglas de "bloquear" que Windows crea
     * para el programa si alguien cerró su aviso: un bloqueo le gana a cualquier permiso.
     */
    fun commands(programPath: String?): List<String> = buildList {
        add("""netsh advfirewall firewall delete rule name="$RULE"""")
        if (programPath != null) add("""netsh advfirewall firewall delete rule name=all dir=in program="$programPath"""")
        add("""netsh advfirewall firewall add rule name="$RULE" dir=in action=allow protocol=TCP localport=$DEFAULT_PORT profile=any remoteip=localsubnet""")
        add("""netsh advfirewall firewall add rule name="$RULE" dir=in action=allow protocol=UDP localport=$DISCOVERY_PORT profile=any remoteip=localsubnet""")
    }

    /** Pide permiso a Windows (un clic en "Sí") y crea la regla. Devuelve si quedó creada. */
    fun requestRule(): Boolean {
        if (!isWindows) return true
        val program = ProcessHandle.current().info().command().orElse(null)
        // En la carpeta Proyecta del usuario y no en Temp: instalado desde Microsoft Store (MSIX),
        // Windows redirige AppData\Temp a una carpeta privada de la app y el cmd con permisos no lo encontraría.
        val dir = File(System.getProperty("user.home"), "Proyecta").apply { mkdirs() }
        val script = File(dir, "permiso-firewall.cmd").apply {
            writeText("@echo off\r\n" + commands(program).joinToString("\r\n") + "\r\n", Charsets.UTF_8)
            deleteOnExit()
        }
        return runCatching {
            val ps = "Start-Process -FilePath '${script.absolutePath}' -Verb RunAs -Wait -WindowStyle Hidden"
            ProcessBuilder("powershell", "-NoProfile", "-Command", ps).redirectErrorStream(true).start()
                .waitFor(120, TimeUnit.SECONDS)
            ruleExists()
        }.getOrDefault(false).also { script.delete() }
    }
}
