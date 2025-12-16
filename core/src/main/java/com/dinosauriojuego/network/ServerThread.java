package com.dinosauriojuego.network;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.dinosauriojuego.pantallas.DinosaurioGameScreen;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;

/**
 * ServerThread - Versión SERVIDOR
 * Maneja la comunicación de red del servidor
 */
public class ServerThread extends Thread {

    private DatagramSocket socket;
    private final int serverPort = 9999;
    private boolean end = false;
    private final int MAX_CLIENTS = 2;
    private int connectedClients = 0;
    private ArrayList<Client> clients = new ArrayList<>();
    private DinosaurioGameScreen gameController;
    private Rectangle hitbox;

    public ServerThread(DinosaurioGameScreen gameController) {
        this.gameController = gameController;
        try {
            socket = new DatagramSocket(serverPort);
            socket.setSoTimeout(0); // Sin timeout
        } catch (SocketException e) {
            System.err.println("❌ Error al crear socket del servidor: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        System.out.println("🟢 Servidor de red iniciado en puerto " + serverPort);

        while (!end) {
            DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
            try {
                socket.receive(packet);
                processMessage(packet);
            } catch (IOException e) {
                if (!end) {
                    System.err.println("❌ Error al recibir paquete: " + e.getMessage());
                }
            }
        }

        System.out.println("🔴 Servidor de red detenido");
    }

    private void processMessage(DatagramPacket packet) {
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
    }

    private void handleConnect(DatagramPacket packet, int clientIndex) {
        if (clientIndex != -1) {
            sendMessage("AlreadyConnected", packet.getAddress(), packet.getPort());
            return;
        }

        if (connectedClients < MAX_CLIENTS) {
            // Asignar el número más bajo disponible
            int playerNum = 1;
            ArrayList<Integer> usados = new ArrayList<>();
            for (Client c : clients) {
                usados.add(c.getNum());
            }
            while (usados.contains(playerNum)) {
                playerNum++;
            }

            Client newClient = new Client(playerNum, packet.getAddress(), packet.getPort());
            clients.add(newClient);
            connectedClients++;

            sendMessage("Connected:" + playerNum, packet.getAddress(), packet.getPort());
            System.out.println("✅ Cliente " + playerNum + " conectado desde " + packet.getAddress() + ":" + packet.getPort());


            if (connectedClients == MAX_CLIENTS) {
                System.out.println("🎮 Todos los jugadores conectados, iniciando juego...");
                for (Client client : clients) {
                    sendMessage("Start", client.getIp(), client.getPort());
                }
            }
        } else {
            sendMessage("Full", packet.getAddress(), packet.getPort());
        }
    }


    private int findClientIndex(DatagramPacket packet) {
        String id = packet.getAddress().toString() + ":" + packet.getPort();

        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).getId().equals(id)) {
                return i;
            }
        }

        return -1;
    }

    public void sendMessage(String message, InetAddress clientIp, int clientPort) {
        if (socket == null || socket.isClosed()) {
            System.err.println("⚠️ Socket cerrado, no se puede enviar: " + message);
            return;
        }

        byte[] byteMessage = message.getBytes();
        DatagramPacket packet = new DatagramPacket(byteMessage, byteMessage.length, clientIp, clientPort);

        try {
            socket.send(packet);
            // System.out.println("📤 [" + clientIp + ":" + clientPort + "] " + message);
        } catch (IOException e) {
            System.err.println("❌ Error al enviar mensaje a " + clientIp + ":" + clientPort + " - " + e.getMessage());
        }
    }





    public void sendMessageToAll(String message) {
        for (Client client : new ArrayList<>(clients)) {
            if (client != null) {
                sendMessage(message, client.getIp(), client.getPort());
            }
        }
    }


    private int findPlayerIndex(InetAddress address, int port) {
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i) != null &&
                    clients.get(i).getIp().equals(address) &&
                    clients.get(i).getPort() == port) {
                return i;
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

        Client clienteDesconectado = clients.get(playerIndex);
        int numPlayerDesconectado = clienteDesconectado.getNum();

        // Remover cliente de la lista
        clients.remove(playerIndex);
        connectedClients = Math.max(connectedClients - 1, 0);

        System.out.println("✅ Jugador " + numPlayerDesconectado + " desconectado");
        System.out.println("👥 Clientes restantes: " + connectedClients);

        // ✅ IMPORTANTE: Notificar al OTRO jugador que su oponente se desconectó
        if (clients.size() > 0) {
            System.out.println("📢 Notificando a jugadores restantes sobre desconexión de jugador " + numPlayerDesconectado);
            sendMessageToAll("WingmanDisconnected:" + numPlayerDesconectado);

            // Dar tiempo para que el mensaje llegue, luego desconectar a todos
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    System.out.println("🔴 Forzando desconexión de jugadores restantes");
                    disconnectAllClients();

                    // ✅ RESETEAR EL SERVIDOR
                    if (gameController != null) {
                        Gdx.app.postRunnable(() -> {
                            gameController.resetearServidorCompleto();
                        });
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            // ✅ Si no quedan clientes, resetear directamente
            System.out.println("📭 No quedan clientes conectados");
            if (gameController != null) {
                Gdx.app.postRunnable(() -> {
                    gameController.resetearServidorCompleto();
                });
            }
        }
    }

    /**
     * ✅ NUEVO: Desconecta a TODOS los clientes y limpia
     */
    public void disconnectAllClients() {
        System.out.println("🔌 Desconectando TODOS los clientes");

        // Enviar mensaje de desconexión a cada cliente
        for (Client client : new ArrayList<>(clients)) {
            sendMessage("ForceDisconnect", client.getIp(), client.getPort());
        }

        // Limpiar la lista
        clients.clear();
        connectedClients = 0;

        System.out.println("✅ Todos los clientes desconectados");
    }

    public void terminate() {
        System.out.println("🛑 Terminando servidor de red...");

        this.end = true;

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        this.interrupt();
    }

    public int getConnectedClients() {
        return connectedClients;
    }
}