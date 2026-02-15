package com.dinosauriojuego.network;

import com.badlogic.gdx.Gdx;
import com.dinosauriojuego.pantallas.DinosaurioGameScreen;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ServerThread - VERSIÓN CORREGIDA
 *
 * Principales correcciones:
 * 1. Eliminado Thread.sleep peligroso
 * 2. Uso de ScheduledExecutorService para tareas asíncronas
 * 3. Mejor manejo de excepciones críticas
 * 4. Timeout más razonable (500ms)
 * 5. Sincronización mejorada
 */
public class ServerThread extends Thread {

    private DatagramSocket socket;
    private final int serverPort = 9999;
    private final AtomicBoolean end = new AtomicBoolean(false);
    private final int MAX_CLIENTS = 2;
    private final AtomicInteger connectedClients = new AtomicInteger(0);

    // Thread-safe collections
    private final ConcurrentHashMap<String, Client> clientsMap = new ConcurrentHashMap<>();
    private final ArrayList<Client> clients = new ArrayList<>();

    private DinosaurioGameScreen gameController;

    // Executor para tareas asíncronas (reemplaza Thread.sleep peligroso)
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ServerThread(DinosaurioGameScreen gameController) {
        super("ServerThread-Main");
        this.gameController = gameController;
        try {
            socket = new DatagramSocket(serverPort);
            socket.setSoTimeout(500); // ✅ Timeout más razonable: 500ms
            System.out.println("🟢 Servidor de red iniciado en puerto " + serverPort);
        } catch (SocketException e) {
            System.err.println("❌ Error CRÍTICO al crear socket del servidor: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("No se pudo iniciar el servidor", e);
        }
    }

    @Override
    public void run() {
        System.out.println("🟢 Servidor de red en ejecución...");

        while (!end.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
                socket.receive(packet);
                processMessage(packet);
            } catch (SocketTimeoutException e) {
                // Timeout normal, continuar
            } catch (SocketException e) {
                if (!end.get()) {
                    System.err.println("❌ ERROR CRÍTICO: Socket cerrado inesperadamente: " + e.getMessage());
                    e.printStackTrace();
                    break; // Salir del loop si el socket se cerró
                }
            } catch (IOException e) {
                if (!end.get()) {
                    System.err.println("⚠️ Error IO al recibir paquete: " + e.getMessage());
                    // NO romper el loop por errores IO normales
                }
            } catch (Exception e) {
                System.err.println("❌ Error INESPERADO en servidor: " + e.getMessage());
                e.printStackTrace();
                // Continuar ejecutando a menos que sea crítico
            }
        }

        System.out.println("🔴 Servidor de red detenido");
        cleanup();
    }

    /**
     * Procesa un mensaje recibido con manejo robusto de errores
     */
    private void processMessage(DatagramPacket packet) {
        try {
            String message = new String(packet.getData(), 0, packet.getLength()).trim();
            String[] parts = message.split(":");

            if (parts.length == 0) {
                System.out.println("⚠️ Mensaje vacío recibido");
                return;
            }

            int clientIndex = findClientIndex(packet);

            System.out.println("📨 [" + packet.getAddress() + ":" + packet.getPort() + "] " + message);

            switch (parts[0]) {
                case "Connect":
                    handleConnect(packet, clientIndex);
                    break;

                case "Disconnect":
                    handleDisconnect(packet.getAddress(), packet.getPort());
                    break;

                default:
                    if (clientIndex == -1) {
                        System.out.println("⚠️ Cliente no conectado intentando enviar: " + parts[0]);
                        sendMessage("NotConnected", packet.getAddress(), packet.getPort());
                    }
                    break;
            }
        } catch (Exception e) {
            System.err.println("❌ Error procesando mensaje: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Maneja nueva conexión
     */
    private void handleConnect(DatagramPacket packet, int clientIndex) {
        try {
            if (clientIndex != -1) {
                sendMessage("AlreadyConnected", packet.getAddress(), packet.getPort());
                return;
            }

            if (connectedClients.get() >= MAX_CLIENTS) {
                sendMessage("Full", packet.getAddress(), packet.getPort());
                System.out.println("⚠️ Servidor lleno, rechazando conexión");
                return;
            }

            // Asignar número de jugador
            int playerNum = 1;
            ArrayList<Integer> usedNumbers = new ArrayList<>();
            synchronized (clients) {
                for (Client c : clients) {
                    usedNumbers.add(c.getNum());
                }
            }
            while (usedNumbers.contains(playerNum)) {
                playerNum++;
            }

            Client newClient = new Client(playerNum, packet.getAddress(), packet.getPort());

            synchronized (clients) {
                clients.add(newClient);
            }
            clientsMap.put(newClient.getId(), newClient);
            connectedClients.incrementAndGet();

            sendMessage("Connected:" + playerNum, packet.getAddress(), packet.getPort());
            System.out.println("✅ Cliente " + playerNum + " conectado desde " +
                    packet.getAddress() + ":" + packet.getPort());

            // Si hay 2 jugadores, iniciar juego
            if (connectedClients.get() == MAX_CLIENTS) {
                System.out.println("🎮 Todos los jugadores conectados, iniciando juego...");
                synchronized (clients) {
                    for (Client client : clients) {
                        sendMessage("Start", client.getIp(), client.getPort());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error en handleConnect: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Maneja desconexión - VERSIÓN CORREGIDA sin Thread.sleep
     */
    private void handleDisconnect(InetAddress address, int port) {
        System.out.println("🔌 Desconectando cliente: " + address + ":" + port);

        int playerIndex = findPlayerIndex(address, port);
        if (playerIndex == -1) {
            System.out.println("⚠️ Cliente no encontrado para desconectar");
            return;
        }

        Client clienteDesconectado;
        synchronized (clients) {
            clienteDesconectado = clients.get(playerIndex);
            clients.remove(playerIndex);
        }

        int numPlayerDesconectado = clienteDesconectado.getNum();
        clientsMap.remove(clienteDesconectado.getId());
        connectedClients.decrementAndGet();

        System.out.println("✅ Jugador " + numPlayerDesconectado + " desconectado");
        System.out.println("👥 Clientes restantes: " + connectedClients.get());

        // Si quedan clientes, notificar y programar reset
        if (connectedClients.get() > 0) {
            System.out.println("📢 Notificando desconexión a jugadores restantes");
            sendMessageToAll("WingmanDisconnected:" + numPlayerDesconectado);

            // ✅ CORRECCIÓN: Usar ScheduledExecutorService en lugar de Thread.sleep
            scheduler.schedule(() -> {
                try {
                    System.out.println("🔴 Ejecutando limpieza programada");
                    disconnectAllClients();

                    // Resetear servidor en el hilo de LibGDX
                    if (gameController != null) {
                        Gdx.app.postRunnable(() -> {
                            try {
                                gameController.resetearServidorCompleto();
                            } catch (Exception e) {
                                System.err.println("❌ Error en resetearServidorCompleto: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error en limpieza programada: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 500, TimeUnit.MILLISECONDS); // 500ms de delay

        } else {
            System.out.println("📭 No quedan clientes conectados");
            if (gameController != null) {
                Gdx.app.postRunnable(() -> {
                    try {
                        gameController.resetearServidorCompleto();
                    } catch (Exception e) {
                        System.err.println("❌ Error en resetearServidorCompleto: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    /**
     * Desconecta a TODOS los clientes de forma segura
     */
    public void disconnectAllClients() {
        System.out.println("🔌 Desconectando TODOS los clientes");

        // Copiar lista para evitar ConcurrentModificationException
        ArrayList<Client> clientsCopy;
        synchronized (clients) {
            clientsCopy = new ArrayList<>(clients);
        }

        // Enviar mensaje de desconexión a cada cliente
        for (Client client : clientsCopy) {
            try {
                sendMessage("ForceDisconnect", client.getIp(), client.getPort());
            } catch (Exception e) {
                System.err.println("⚠️ Error enviando ForceDisconnect a cliente: " + e.getMessage());
            }
        }

        // Limpiar estructuras
        synchronized (clients) {
            clients.clear();
        }
        clientsMap.clear();
        connectedClients.set(0);

        System.out.println("✅ Todos los clientes desconectados");
    }

    /**
     * Envía mensaje a un cliente específico con manejo de errores
     */
    public void sendMessage(String message, InetAddress clientIp, int clientPort) {
        if (socket == null || socket.isClosed()) {
            System.err.println("⚠️ Socket cerrado, no se puede enviar: " + message);
            return;
        }

        try {
            byte[] byteMessage = message.getBytes();
            DatagramPacket packet = new DatagramPacket(byteMessage, byteMessage.length, clientIp, clientPort);
            socket.send(packet);
        } catch (IOException e) {
            if (!end.get()) {
                System.err.println("❌ Error al enviar mensaje a " + clientIp + ":" + clientPort +
                        " - " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("❌ Error inesperado al enviar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Envía mensaje a todos los clientes conectados
     */
    public void sendMessageToAll(String message) {
        ArrayList<Client> clientsCopy;
        synchronized (clients) {
            clientsCopy = new ArrayList<>(clients);
        }

        for (Client client : clientsCopy) {
            if (client != null) {
                sendMessage(message, client.getIp(), client.getPort());
            }
        }
    }

    private int findClientIndex(DatagramPacket packet) {
        String id = packet.getAddress().toString() + ":" + packet.getPort();
        synchronized (clients) {
            for (int i = 0; i < clients.size(); i++) {
                if (clients.get(i).getId().equals(id)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findPlayerIndex(InetAddress address, int port) {
        synchronized (clients) {
            for (int i = 0; i < clients.size(); i++) {
                if (clients.get(i) != null &&
                        clients.get(i).getIp().equals(address) &&
                        clients.get(i).getPort() == port) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Limpieza de recursos al terminar
     */
    private void cleanup() {
        try {
            // Apagar el scheduler
            scheduler.shutdown();
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Cerrar socket
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error al cerrar socket: " + e.getMessage());
        }
    }

    /**
     * Detiene el servidor de forma segura
     */
    public void terminate() {
        System.out.println("🛑 Terminando servidor de red...");
        end.set(true);
        cleanup();
        this.interrupt();
    }

    public int getConnectedClients() {
        return connectedClients.get();
    }
}