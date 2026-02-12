package com.dinosauriojuego.network;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.dinosauriojuego.pantallas.DinosaurioGameScreen;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ServerThread - Versión MEJORADA para evitar cierres inesperados
 * Maneja la comunicación de red del servidor con thread-safety
 */
public class ServerThread extends Thread {

    private DatagramSocket socket;
    private final int serverPort = 9999;
    private final AtomicBoolean end = new AtomicBoolean(false);
    private final int MAX_CLIENTS = 2;
    private final AtomicInteger connectedClients = new AtomicInteger(0);

    // Usar ConcurrentHashMap para thread-safety
    private final ConcurrentHashMap<String, Client> clientsMap = new ConcurrentHashMap<>();
    private final ArrayList<Client> clients = new ArrayList<>();

    private DinosaurioGameScreen gameController;
    private Rectangle hitbox;

    public ServerThread(DinosaurioGameScreen gameController) {
        super("ServerThread-Main");
        this.gameController = gameController;
        try {
            socket = new DatagramSocket(serverPort);
            socket.setSoTimeout(100); // ✅ Timeout razonable para evitar bloqueos
        } catch (SocketException e) {
            System.err.println("❌ Error al crear socket del servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        System.out.println("🟢 Servidor de red iniciado en puerto " + serverPort);

        while (!end.get()) {
            DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
            try {
                socket.receive(packet);
                processMessage(packet);
            } catch (java.net.SocketTimeoutException e) {
                // Timeout normal, continuar
            } catch (IOException e) {
                if (!end.get()) {
                    System.err.println("❌ Error al recibir paquete: " + e.getMessage());
                }
            } catch (Exception e) {
                if (!end.get()) {
                    System.err.println("❌ Error inesperado: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        System.out.println("🔴 Servidor de red detenido");
    }

    private void processMessage(DatagramPacket packet) {
        try {
            String message = (new String(packet.getData())).trim();
            String[] parts = message.split(":");
            int clientIndex = findClientIndex(packet);

            System.out.println("📨 [" + packet.getAddress() + ":" + packet.getPort() + "] " + message);

            switch (parts[0]) {
                case "Connect":
                    handleConnect(packet, clientIndex);
                    break;

                case "Disconnect":
                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();
                    System.out.println("🔌 Cliente solicitó desconexión: " + address + ":" + port);
                    desconectarCliente(address, port);
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

    private void handleConnect(DatagramPacket packet, int clientIndex) {
        try {
            if (clientIndex != -1) {
                sendMessage("AlreadyConnected", packet.getAddress(), packet.getPort());
                return;
            }

            if (connectedClients.get() < MAX_CLIENTS) {
                // Asignar el número más bajo disponible
                int playerNum = 1;
                ArrayList<Integer> usados = new ArrayList<>();
                synchronized (clients) {
                    for (Client c : clients) {
                        usados.add(c.getNum());
                    }
                }
                while (usados.contains(playerNum)) {
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

                if (connectedClients.get() == MAX_CLIENTS) {
                    System.out.println("🎮 Todos los jugadores conectados, iniciando juego...");
                    synchronized (clients) {
                        for (Client client : clients) {
                            sendMessage("Start", client.getIp(), client.getPort());
                        }
                    }
                }
            } else {
                sendMessage("Full", packet.getAddress(), packet.getPort());
            }
        } catch (Exception e) {
            System.err.println("❌ Error en handleConnect: " + e.getMessage());
            e.printStackTrace();
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
                System.err.println("❌ Error al enviar mensaje a " + clientIp + ":" + clientPort + " - " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("❌ Error inesperado al enviar: " + e.getMessage());
            e.printStackTrace();
        }
    }

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

    public void desconectarCliente(InetAddress address, int port) {
        System.out.println("🔌 Desconectando cliente: " + address + ":" + port);

        int playerIndex = findPlayerIndex(address, port);
        if (playerIndex == -1) {
            System.out.println("⚠️ Cliente no encontrado para desconectar (ya fue removido)");
            return;
        }

        Client clienteDesconectado;
        synchronized (clients) {
            clienteDesconectado = clients.get(playerIndex);
        }

        int numPlayerDesconectado = clienteDesconectado.getNum();

        // Remover cliente de las estructuras
        synchronized (clients) {
            clients.remove(playerIndex);
        }
        clientsMap.remove(clienteDesconectado.getId());
        connectedClients.decrementAndGet();

        System.out.println("✅ Jugador " + numPlayerDesconectado + " desconectado");
        System.out.println("👥 Clientes restantes: " + connectedClients.get());

        // Notificar al OTRO jugador que su oponente se desconectó
        if (connectedClients.get() > 0) {
            System.out.println("📢 Notificando a jugadores restantes sobre desconexión de jugador " + numPlayerDesconectado);
            sendMessageToAll("WingmanDisconnected:" + numPlayerDesconectado);

            // ✅ Usar un ExecutorService o Timer en lugar de Thread.sleep peligroso
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    System.out.println("🔴 Forzando desconexión de jugadores restantes");
                    disconnectAllClients();

                    // Resetear el servidor
                    if (gameController != null) {
                        Gdx.app.postRunnable(() -> {
                            gameController.resetearServidorCompleto();
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("⚠️ Thread de desconexión interrumpido");
                } catch (Exception e) {
                    System.err.println("❌ Error en desconexión: " + e.getMessage());
                    e.printStackTrace();
                }
            }, "DisconnectHandler").start();
        } else {
            // Si no quedan clientes, resetear directamente
            System.out.println("📭 No quedan clientes conectados");
            if (gameController != null) {
                Gdx.app.postRunnable(() -> {
                    gameController.resetearServidorCompleto();
                });
            }
        }
    }

    /**
     * Desconecta a TODOS los clientes y limpia
     */
    public void disconnectAllClients() {
        System.out.println("🔌 Desconectando TODOS los clientes");

        ArrayList<Client> clientsCopy;
        synchronized (clients) {
            clientsCopy = new ArrayList<>(clients);
        }

        // Enviar mensaje de desconexión a cada cliente
        for (Client client : clientsCopy) {
            sendMessage("ForceDisconnect", client.getIp(), client.getPort());
        }

        // Limpiar las estructuras
        synchronized (clients) {
            clients.clear();
        }
        clientsMap.clear();
        connectedClients.set(0);

        System.out.println("✅ Todos los clientes desconectados");
    }

    public void terminate() {
        System.out.println("🛑 Terminando servidor de red...");

        end.set(true);

        // Cerrar socket de forma segura
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error al cerrar socket: " + e.getMessage());
        }

        this.interrupt();
    }

    public int getConnectedClients() {
        return connectedClients.get();
    }
}