package com.dinosauriojuego.servidor.network;

import com.dinosauriojuego.servidor.logica.GameSimulacion;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor UDP mejorado con mejor manejo de concurrencia y estabilidad
 */
public class HiloServidor extends Thread {

    private static final int PUERTO = 8999;
    private static final int MAX_CLIENTES = 2;
    private static final int TICK_MS = 16; // 60 FPS
    private static final int SOCKET_TIMEOUT_MS = 100; // Timeout razonable

    private DatagramSocket socket;
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Usar estructuras thread-safe
    private final ConcurrentHashMap<String, ClientInfo> clientes = new ConcurrentHashMap<>();
    private final AtomicInteger cantidadClientes = new AtomicInteger(0);
    private final AtomicBoolean juegoIniciado = new AtomicBoolean(false);

    // Inputs de los jugadores (acumulados entre frames)
    private volatile boolean j1Saltar = false;
    private volatile boolean j1Agachar = false;
    private volatile boolean j2Saltar = false;
    private volatile boolean j2Agachar = false;

    // Simulación del juego
    private final GameSimulacion simulacion = new GameSimulacion();
    private final AtomicInteger tick = new AtomicInteger(0);

    /**
     * Clase para almacenar información del cliente de forma thread-safe
     */
    private static class ClientInfo {
        final InetAddress ip;
        final int puerto;
        final int numeroJugador;
        volatile boolean listo = false;
        volatile boolean resetReady = false;

        ClientInfo(InetAddress ip, int puerto, int numeroJugador) {
            this.ip = ip;
            this.puerto = puerto;
            this.numeroJugador = numeroJugador;
        }

        String getId() {
            return ip.getHostAddress() + ":" + puerto;
        }
    }

    public HiloServidor() {
        super("ServidorUDP-Thread");
        setDaemon(false); // Thread NO daemon para control manual

        try {
            socket = new DatagramSocket(PUERTO);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS); // Timeout para no bloquear indefinidamente
            System.out.println("🟢 Servidor UDP iniciado en puerto " + PUERTO);
        } catch (Exception e) {
            System.err.println("❌ Error al crear servidor: " + e.getMessage());
            throw new RuntimeException("Error al crear servidor: " + e.getMessage(), e);
        }
    }

    @Override
    public void run() {
        long ultimoTick = System.currentTimeMillis();

        System.out.println("🔄 Servidor en ejecución...");

        while (running.get()) {
            try {
                // Recibir mensajes de clientes
                recibirMensajes();

                // Simular juego si está iniciado
                if (juegoIniciado.get()) {
                    long ahora = System.currentTimeMillis();
                    if (ahora - ultimoTick >= TICK_MS) {
                        actualizarSimulacion();
                        ultimoTick = ahora;
                    }
                }

            } catch (Exception e) {
                if (running.get()) {
                    System.err.println("⚠️ Error en loop principal: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        cerrarSocket();
        System.out.println("🔴 Servidor detenido correctamente");
    }

    /**
     * Actualiza la simulación del juego
     */
    private void actualizarSimulacion() {
        if (!simulacion.terminado) {
            simulacion.actualizar(
                    TICK_MS / 1000f,
                    j1Saltar, j1Agachar,
                    j2Saltar, j2Agachar
            );
        }

        // Reset los inputs "just pressed"
        j1Saltar = false;
        j2Saltar = false;

        // Verificar reset
        if (simulacion.terminado && contarResetReady() == 2) {
            reiniciarJuego();
        }

        tick.incrementAndGet();
        enviarSnapshot();
    }

    /**
     * Recibe mensajes de los clientes con manejo de excepciones robusto
     */
    private void recibirMensajes() {
        try {
            byte[] buffer = new byte[512];
            DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
            socket.receive(paquete);

            String mensaje = new String(paquete.getData(), 0, paquete.getLength(),
                    StandardCharsets.UTF_8).trim();

            procesarMensaje(mensaje, paquete.getAddress(), paquete.getPort());

        } catch (java.net.SocketTimeoutException e) {
            // Timeout es normal, continuar
        } catch (java.net.SocketException e) {
            if (running.get()) {
                System.err.println("❌ Error de socket: " + e.getMessage());
            }
        } catch (Exception e) {
            if (running.get()) {
                System.err.println("❌ Error al recibir mensaje: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Procesa un mensaje recibido
     */
    private void procesarMensaje(String mensaje, InetAddress ip, int puerto) {
        try {
            String clienteId = ip.getHostAddress() + ":" + puerto;
            ClientInfo cliente = clientes.get(clienteId);

            // Mensaje de conexión
            if (mensaje.equals("Conexion")) {
                manejarConexion(ip, puerto);
                return;
            }

            // Si el cliente no está registrado, ignorar
            if (cliente == null) {
                System.out.println("⚠️ Mensaje de cliente no registrado: " + mensaje);
                return;
            }

            // Mensaje de listo
            if (mensaje.equals("Listo")) {
                cliente.listo = true;
                System.out.println("✅ Cliente " + cliente.numeroJugador + " listo");

                if (todosListos()) {
                    iniciarJuego();
                }
                return;
            }

            // Mensaje de reset
            if (mensaje.equals("RESET")) {
                if (simulacion.terminado) {
                    cliente.resetReady = true;
                    System.out.println("🔄 Cliente " + cliente.numeroJugador +
                            " listo para reset (" + contarResetReady() + "/2)");
                }
                return;
            }

            // Mensaje de input
            if (mensaje.startsWith("INPUT;")) {
                procesarInput(mensaje, cliente);
            }

        } catch (Exception e) {
            System.err.println("❌ Error al procesar mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Procesa los inputs de un jugador
     */
    private void procesarInput(String mensaje, ClientInfo cliente) {
        if (simulacion.terminado) {
            return;
        }

        try {
            String[] partes = mensaje.split(";");
            if (partes.length < 3) {
                return;
            }

            boolean saltar = "1".equals(partes[1]);
            boolean agachar = "1".equals(partes[2]);

            // Asignar inputs según número de jugador
            if (cliente.numeroJugador == 1) {
                j1Saltar = j1Saltar || saltar;
                j1Agachar = agachar;
            } else if (cliente.numeroJugador == 2) {
                j2Saltar = j2Saltar || saltar;
                j2Agachar = agachar;
            }

        } catch (Exception e) {
            System.err.println("❌ Error al procesar input: " + e.getMessage());
        }
    }

    /**
     * Maneja una nueva conexión de cliente
     */
    private synchronized void manejarConexion(InetAddress ip, int puerto) {
        String clienteId = ip.getHostAddress() + ":" + puerto;

        // Verificar si ya está conectado
        if (clientes.containsKey(clienteId)) {
            enviarMensaje("OK", ip, puerto);
            System.out.println("⚠️ Cliente ya conectado: " + clienteId);
            return;
        }

        // Verificar si hay espacio
        if (cantidadClientes.get() >= MAX_CLIENTES) {
            enviarMensaje("Full", ip, puerto);
            System.out.println("⚠️ Servidor lleno, rechazando: " + clienteId);
            return;
        }

        // Determinar número de jugador (1 o 2)
        int numeroJugador = cantidadClientes.get() + 1;

        // Registrar nuevo cliente
        ClientInfo nuevoCliente = new ClientInfo(ip, puerto, numeroJugador);
        clientes.put(clienteId, nuevoCliente);
        cantidadClientes.incrementAndGet();

        enviarMensaje("OK", ip, puerto);
        System.out.println("✅ Cliente " + numeroJugador + " conectado: " + clienteId +
                " (total: " + cantidadClientes.get() + "/2)");
    }

    /**
     * Verifica si todos los clientes están listos
     */
    private boolean todosListos() {
        if (cantidadClientes.get() < MAX_CLIENTES) {
            return false;
        }

        for (ClientInfo cliente : clientes.values()) {
            if (!cliente.listo) {
                return false;
            }
        }

        return true;
    }

    /**
     * Inicia el juego cuando ambos clientes están listos
     */
    private synchronized void iniciarJuego() {
        if (juegoIniciado.get()) {
            return;
        }

        juegoIniciado.set(true);
        simulacion.reset();
        tick.set(0);

        // Resetear flags de reset
        for (ClientInfo cliente : clientes.values()) {
            cliente.resetReady = false;
        }

        broadcast("Empieza");
        System.out.println("🎮 ¡JUEGO INICIADO!");
    }

    /**
     * Reinicia el juego
     */
    private synchronized void reiniciarJuego() {
        System.out.println("🔄 Reiniciando juego...");

        simulacion.reset();
        tick.set(0);

        // Resetear flags de reset
        for (ClientInfo cliente : clientes.values()) {
            cliente.resetReady = false;
        }

        j1Saltar = j2Saltar = false;
        j1Agachar = j2Agachar = false;
    }

    /**
     * Envía un snapshot del estado del juego a todos los clientes
     */
    private void enviarSnapshot() {
        StringBuilder sb = new StringBuilder("SNAP;");

        // Información general
        sb.append(tick.get()).append(";")
                .append(simulacion.puntuacion).append(";")
                .append(simulacion.velocidad).append(";")
                .append(juegoIniciado.get() ? 1 : 0).append(";")
                .append(simulacion.terminado ? 1 : 0).append(";")
                .append(seguro(simulacion.mensajeFin)).append(";")
                .append(contarResetReady()).append(";");

        // Jugador 1
        sb.append(simulacion.jugador1.y).append(";")
                .append(simulacion.jugador1.enSuelo ? 1 : 0).append(";")
                .append(simulacion.jugador1.agachado ? 1 : 0).append(";")
                .append(simulacion.jugador1.vivo ? 1 : 0).append(";");

        // Jugador 2
        sb.append(simulacion.jugador2.y).append(";")
                .append(simulacion.jugador2.enSuelo ? 1 : 0).append(";")
                .append(simulacion.jugador2.agachado ? 1 : 0).append(";")
                .append(simulacion.jugador2.vivo ? 1 : 0).append(";");

        // Obstáculos
        sb.append(simulacion.obstaculos.size()).append(";");
        for (GameSimulacion.EstadoObstaculo obs : simulacion.obstaculos) {
            sb.append(obs.tipo).append(";")
                    .append(obs.variante).append(";")
                    .append(obs.x).append(";")
                    .append(obs.y).append(";");
        }

        broadcast(sb.toString());
    }

    /**
     * Envía un mensaje a un cliente específico
     */
    private void enviarMensaje(String mensaje, InetAddress ip, int puerto) {
        if (socket == null || socket.isClosed()) {
            System.err.println("⚠️ Socket cerrado, no se puede enviar: " + mensaje);
            return;
        }

        try {
            byte[] datos = mensaje.getBytes(StandardCharsets.UTF_8);
            DatagramPacket paquete = new DatagramPacket(datos, datos.length, ip, puerto);
            socket.send(paquete);
        } catch (Exception e) {
            if (running.get()) {
                System.err.println("❌ Error al enviar mensaje: " + e.getMessage());
            }
        }
    }

    /**
     * Envía un mensaje a todos los clientes conectados
     */
    private void broadcast(String mensaje) {
        for (ClientInfo cliente : clientes.values()) {
            enviarMensaje(mensaje, cliente.ip, cliente.puerto);
        }
    }

    /**
     * Cuenta cuántos clientes están listos para reset
     */
    private int contarResetReady() {
        int count = 0;
        for (ClientInfo cliente : clientes.values()) {
            if (cliente.resetReady) {
                count++;
            }
        }
        return count;
    }

    /**
     * Hace seguro un string para el protocolo (evita ; que rompe el split)
     */
    private String seguro(String s) {
        if (s == null) return "";
        return s.replace(";", ",");
    }

    /**
     * Cierra el socket de forma segura
     */
    private void cerrarSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("🔌 Socket cerrado correctamente");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error al cerrar socket: " + e.getMessage());
        }
    }

    /**
     * Detiene el servidor de forma segura
     */
    public void cerrar() {
        System.out.println("🛑 Cerrando servidor...");

        running.set(false);

        // Notificar a todos los clientes
        broadcast("Desconectar");

        // Esperar un momento para que llegue el mensaje
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        cerrarSocket();
        interrupt();
    }

    // Getters para la UI
    public GameSimulacion getSimulacion() {
        return simulacion;
    }

    public int getCantidadClientes() {
        return cantidadClientes.get();
    }

    public boolean isJuegoIniciado() {
        return juegoIniciado.get();
    }

    public int getTick() {
        return tick.get();
    }
}