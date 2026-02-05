package com.dinosauriojuego.servidor.network;

import com.dinosauriojuego.servidor.logica.GameSimulacion;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Hilo del servidor para comunicación UDP con los clientes
 * Maneja la red, recibe inputs y envía snapshots
 */
public class HiloServidor extends Thread {

    private static final int PUERTO = 8999;
    private static final int MAX_CLIENTES = 2;
    private static final int TICK_MS = 16; // 60 FPS

    private DatagramSocket socket;
    private volatile boolean running = true;

    // Información de clientes
    private final InetAddress[] clientesIP = new InetAddress[MAX_CLIENTES];
    private final int[] clientesPuerto = new int[MAX_CLIENTES];
    private final boolean[] clientesListos = new boolean[MAX_CLIENTES];
    private final boolean[] clientesResetReady = new boolean[MAX_CLIENTES];

    private int cantidadClientes = 0;
    private boolean juegoIniciado = false;

    // Inputs de los jugadores (acumulados entre frames)
    private boolean j1Saltar = false;
    private boolean j1Agachar = false;
    private boolean j2Saltar = false;
    private boolean j2Agachar = false;

    // Simulación del juego
    private final GameSimulacion simulacion = new GameSimulacion();
    private int tick = 0;

    public HiloServidor() {
        try {
            socket = new DatagramSocket(PUERTO);
            socket.setSoTimeout(5); // Timeout corto para el loop
            System.out.println("🟢 Servidor UDP iniciado en puerto " + PUERTO);
        } catch (Exception e) {
            throw new RuntimeException("Error al crear servidor: " + e.getMessage(), e);
        }
    }

    @Override
    public void run() {
        long ultimoTick = System.currentTimeMillis();

        while (running) {
            // Recibir mensajes de clientes
            recibirMensajes();

            // Simular juego si está iniciado
            if (juegoIniciado) {
                long ahora = System.currentTimeMillis();
                if (ahora - ultimoTick >= TICK_MS) {
                    // Simular un frame
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

                    tick++;
                    enviarSnapshot();

                    ultimoTick = ahora;
                }
            }
        }

        cerrarSocket();
        System.out.println("🔴 Servidor detenido");
    }

    /**
     * Recibe mensajes de los clientes
     */
    private void recibirMensajes() {
        try {
            byte[] buffer = new byte[512];
            DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
            socket.receive(paquete);

            String mensaje = new String(paquete.getData(), 0, paquete.getLength(),
                    StandardCharsets.UTF_8).trim();
            procesarMensaje(mensaje, paquete.getAddress(), paquete.getPort());

        } catch (Exception ignored) {
            // Timeout es normal, no hacer nada
        }
    }

    /**
     * Procesa un mensaje recibido
     */
    private void procesarMensaje(String mensaje, InetAddress ip, int puerto) {
        int indiceCliente = buscarIndiceCliente(ip, puerto);

        // Mensaje de conexión
        if (mensaje.equals("Conexion")) {
            manejarConexion(ip, puerto);
            return;
        }

        // Si el cliente no está registrado, ignorar
        if (indiceCliente == -1) {
            return;
        }

        // Mensaje de listo
        if (mensaje.equals("Listo")) {
            clientesListos[indiceCliente] = true;
            System.out.println("✅ Cliente " + (indiceCliente + 1) + " listo");

            if (clientesListos[0] && clientesListos[1] && !juegoIniciado) {
                iniciarJuego();
            }
            return;
        }

        // Mensaje de reset
        if (mensaje.equals("RESET")) {
            if (simulacion.terminado) {
                clientesResetReady[indiceCliente] = true;
                System.out.println("🔄 Cliente " + (indiceCliente + 1) +
                        " listo para reset (" + contarResetReady() + "/2)");
            }
            return;
        }

        // Mensaje de input
        if (mensaje.startsWith("INPUT;")) {
            procesarInput(mensaje, indiceCliente);
        }
    }

    /**
     * Procesa los inputs de un jugador
     */
    private void procesarInput(String mensaje, int indiceCliente) {
        if (simulacion.terminado) {
            return;
        }

        String[] partes = mensaje.split(";");
        if (partes.length < 3) {
            return;
        }

        boolean saltar = partes[1].equals("1");
        boolean agachar = partes[2].equals("1");

        if (indiceCliente == 0) {
            j1Saltar = j1Saltar || saltar; // OR para no perder inputs
            j1Agachar = agachar;
        } else {
            j2Saltar = j2Saltar || saltar;
            j2Agachar = agachar;
        }
    }

    /**
     * Maneja una nueva conexión
     */
    private void manejarConexion(InetAddress ip, int puerto) {
        // Verificar si ya está conectado
        for (int i = 0; i < cantidadClientes; i++) {
            if (clientesIP[i].equals(ip) && clientesPuerto[i] == puerto) {
                enviarMensaje("OK", ip, puerto);
                return;
            }
        }

        // Verificar si hay espacio
        if (cantidadClientes >= MAX_CLIENTES) {
            enviarMensaje("Full", ip, puerto);
            return;
        }

        // Registrar nuevo cliente
        clientesIP[cantidadClientes] = ip;
        clientesPuerto[cantidadClientes] = puerto;
        clientesListos[cantidadClientes] = false;
        clientesResetReady[cantidadClientes] = false;

        enviarMensaje("OK", ip, puerto);
        cantidadClientes++;

        System.out.println("✅ Cliente conectado: " + ip.getHostAddress() + ":" + puerto +
                " (total: " + cantidadClientes + "/2)");
    }

    /**
     * Inicia el juego cuando ambos clientes están listos
     */
    private void iniciarJuego() {
        juegoIniciado = true;
        simulacion.reset();
        tick = 0;
        clientesResetReady[0] = false;
        clientesResetReady[1] = false;

        broadcast("Empieza");
        System.out.println("🎮 ¡JUEGO INICIADO!");
    }

    /**
     * Reinicia el juego
     */
    private void reiniciarJuego() {
        System.out.println("🔄 Reiniciando juego...");

        simulacion.reset();
        tick = 0;
        clientesResetReady[0] = false;
        clientesResetReady[1] = false;

        j1Saltar = j2Saltar = false;
        j1Agachar = j2Agachar = false;
    }

    /**
     * Envía un snapshot del estado del juego a todos los clientes
     */
    private void enviarSnapshot() {
        StringBuilder sb = new StringBuilder("SNAP;");

        // Información general
        sb.append(tick).append(";")
                .append(simulacion.puntuacion).append(";")
                .append(simulacion.velocidad).append(";")
                .append(juegoIniciado ? 1 : 0).append(";")
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
        try {
            byte[] datos = mensaje.getBytes(StandardCharsets.UTF_8);
            DatagramPacket paquete = new DatagramPacket(datos, datos.length, ip, puerto);
            socket.send(paquete);
        } catch (Exception e) {
            System.err.println("❌ Error al enviar mensaje: " + e.getMessage());
        }
    }

    /**
     * Envía un mensaje a todos los clientes conectados
     */
    private void broadcast(String mensaje) {
        for (int i = 0; i < cantidadClientes; i++) {
            enviarMensaje(mensaje, clientesIP[i], clientesPuerto[i]);
        }
    }

    /**
     * Busca el índice de un cliente por IP y puerto
     */
    private int buscarIndiceCliente(InetAddress ip, int puerto) {
        for (int i = 0; i < cantidadClientes; i++) {
            if (clientesIP[i].equals(ip) && clientesPuerto[i] == puerto) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Cuenta cuántos clientes están listos para reset
     */
    private int contarResetReady() {
        int count = 0;
        for (int i = 0; i < MAX_CLIENTES; i++) {
            if (clientesResetReady[i]) count++;
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
     * Cierra el socket
     */
    private void cerrarSocket() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    /**
     * Detiene el servidor
     */
    public void cerrar() {
        System.out.println("🛑 Cerrando servidor...");
        running = false;
        interrupt();
    }

    // Getters para la UI
    public GameSimulacion getSimulacion() {
        return simulacion;
    }

    public int getCantidadClientes() {
        return cantidadClientes;
    }

    public boolean isJuegoIniciado() {
        return juegoIniciado;
    }

    public int getTick() {
        return tick;
    }
}