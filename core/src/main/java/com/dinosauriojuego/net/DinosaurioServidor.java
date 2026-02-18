package com.dinosauriojuego.net;

import com.dinosauriojuego.server.DinosaurioGame;
import com.dinosauriojuego.server.DinosaurioGame;
import com.dinosauriojuego.server.SnapshotDino;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

//el servidor recibe inputs, controla el ritmo del juego y distribuye snapshots para mantener a todos sincronizados.
public class DinosaurioServidor extends Thread {

    public static final int PUERTO = 8999;
    private static final int MAX_CLIENTES = 2;
    private static final int TICK_MS = 16;

    private DatagramSocket socket;
    private volatile boolean running = true;

    //guarda quienes son los clientes, si estan listos y si estan listos para reset
    private final InetAddress[] clientesIP   = new InetAddress[MAX_CLIENTES];
    private final int[]         clientesPort = new int[MAX_CLIENTES];
    private final boolean[]     clientesListos = new boolean[MAX_CLIENTES];
    private final boolean[]     resetReady   = new boolean[MAX_CLIENTES];

    private int cantClientes = 0;
    private boolean partidaIniciada = false;

    //inputs de cada jugador
    private boolean j1Jump, j1Crouch;
    private boolean j2Jump, j2Crouch;

    private final DinosaurioGame sim = new DinosaurioGame();
    private int tick = 0;
    private SnapshotDino lastSnapshot;

    public DinosaurioServidor() {
        try {
            socket = new DatagramSocket(PUERTO);
            socket.setSoTimeout(5);
            System.out.println("[SERVER] UDP escuchando en puerto " + PUERTO);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    //revisa si hay mensajes recibidos, si la partida comenzo y simula el juego enviando snapshots a los clientes
    public void run() {
        long lastTime = System.currentTimeMillis();
        while (running) {
            recibirMensajes();

            if (partidaIniciada) {
                long now = System.currentTimeMillis();
                if (now - lastTime >= TICK_MS) {

                    //si termino, no simula mas hasta reiniciar
                    if (!sim.terminado) {
                        sim.step(TICK_MS / 1000f, j1Jump, j1Crouch, j2Jump, j2Crouch);
                    }

                    // jump es "just pressed", se resetea despues de cada tick
                    j1Jump = false;
                    j2Jump = false;

                    // si ambos pidieron reset, reiniciar
                    if (sim.terminado && resetReadyCount() == 2) {
                        reiniciarPartida();
                    }

                    tick++;
                    lastSnapshot = crearSnapshot();
                    enviarSnapshot();

                    lastTime = now;
                }
            }
        }

        try { socket.close(); } catch (Exception ignored) {}
    }

    //recibe el mensaje y lo manda a procesar
    private void recibirMensajes() {
        try {
            byte[] buf = new byte[512];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            socket.receive(dp);
            String msg = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8).trim();
            procesarMensaje(msg, dp.getAddress(), dp.getPort());
        } catch (Exception ignored) {}
    }

    //procesa el mensaje y decide que hacer segun el tipo
    private void procesarMensaje(String msg, InetAddress ip, int port) {
        if (msg.equals("Conexion")) {
            registrarCliente(ip, port);
            return;
        }

        int idx = indexCliente(ip, port);
        if (idx == -1) return;

        //verifica que el cliente este listo para empezar la partida
        if (msg.equals("Listo")) {
            clientesListos[idx] = true;
            System.out.println("[SERVER] Cliente " + (idx + 1) + " listo");
            if (clientesListos[0] && clientesListos[1] && !partidaIniciada) {
                iniciarPartida();
            }
            return;
        }

        //verifica si el cliente quiere reiniciar la partida
        if (msg.equals("RESET")) {
            if (sim.terminado) {
                resetReady[idx] = true;
                System.out.println("[SERVER] RESET ready de jugador " + (idx + 1) + " (" + resetReadyCount() + "/2)");
            }
            return;
        }

        //procesa los inputs del cliente (jump y crouch)
        if (msg.startsWith("INPUT;")) {
            String[] p = msg.split(";");
            if (p.length < 3) return;

            boolean jump   = p[1].equals("1");
            boolean crouch = p[2].equals("1");

            if (sim.terminado) return;

            if (idx == 0) {
                j1Jump   = jump || j1Jump; // no perder saltos entre ticks
                j1Crouch = crouch;
            } else {
                j2Jump   = jump || j2Jump;
                j2Crouch = crouch;
            }
        }
    }

    //registra un nuevo cliente si no esta lleno y no es un duplicado
    private void registrarCliente(InetAddress ip, int port) {
        for (int i = 0; i < cantClientes; i++) {
            if (clientesIP[i].equals(ip) && clientesPort[i] == port) {
                enviar("OK", ip, port);
                return;
            }
        }

        if (cantClientes >= MAX_CLIENTES) return; // servidor lleno

        clientesIP[cantClientes]    = ip;
        clientesPort[cantClientes]  = port;
        clientesListos[cantClientes] = false;
        resetReady[cantClientes]    = false;

        enviar("OK", ip, port);
        cantClientes++;

        System.out.println("[SERVER] Cliente conectado: " + ip.getHostAddress() + ":" + port + " (total " + cantClientes + "/2)");
    }

    //inicia la partida cuando ambos clientes estan listos
    private void iniciarPartida() {
        partidaIniciada = true;
        sim.reset();
        tick = 0;
        resetReady[0] = false;
        resetReady[1] = false;
        broadcast("Empieza");
        System.out.println("[SERVER] PARTIDA INICIADA");
    }

    //reinicia la partida cuando ambos clientes lo pidieron
    private void reiniciarPartida() {
        System.out.println("[SERVER] REINICIO (ambos apretaron R)");
        sim.reset();
        tick = 0;
        resetReady[0]  = false;
        resetReady[1]  = false;
        j1Jump = j2Jump = false;
        j1Crouch = j2Crouch = false;
    }

    //crea un snapshot del estado actual del juego
    private SnapshotDino crearSnapshot() {
        SnapshotDino s = new SnapshotDino();
        s.tick      = tick;
        s.score     = sim.score;
        s.velocidad = sim.velocidad;

        s.started    = partidaIniciada;
        s.terminado  = sim.terminado;
        s.mensajeFin = sim.mensajeFin;

        s.resetReadyCount = resetReadyCount();

        s.p1.y        = sim.p1.y;
        s.p1.vy       = sim.p1.vy;
        s.p1.enPiso   = sim.p1.enPiso;
        s.p1.agachado = sim.p1.agachado;
        s.p1.vivo     = sim.p1.vivo;

        s.p2.y        = sim.p2.y;
        s.p2.vy       = sim.p2.vy;
        s.p2.enPiso   = sim.p2.enPiso;
        s.p2.agachado = sim.p2.agachado;
        s.p2.vivo     = sim.p2.vivo;

        // copiar obstaculos de la pista 1
        for (DinosaurioGame.Obstacle o : sim.obstaculosP1) {
            SnapshotDino.ObstacleState os = new SnapshotDino.ObstacleState();
            os.type    = o.type;
            os.variant = o.variant;
            os.x       = o.x;
            os.y       = o.y;
            s.obstaculosP1.add(os);
        }

        // copiar obstaculos de la pista 2
        for (DinosaurioGame.Obstacle o : sim.obstaculosP2) {
            SnapshotDino.ObstacleState os = new SnapshotDino.ObstacleState();
            os.type    = o.type;
            os.variant = o.variant;
            os.x       = o.x;
            os.y       = o.y;
            s.obstaculosP2.add(os);
        }

        return s;
    }

    //envia el snapshot serializado a todos los clientes
    //formato: SNAP;tick;score;vel;started;terminado;mensajeFin;resetCount;
    //         p1y;p1piso;p1agach;p1vivo;  p2y;p2piso;p2agach;p2vivo;
    //         cantP1;[type;variant;x;y;]*cantP1
    //         cantP2;[type;variant;x;y;]*cantP2
    private void enviarSnapshot() {
        if (lastSnapshot == null) return;

        StringBuilder sb = new StringBuilder("SNAP;");
        sb.append(lastSnapshot.tick).append(";")
                .append(lastSnapshot.score).append(";")
                .append(lastSnapshot.velocidad).append(";")
                .append(lastSnapshot.started   ? 1 : 0).append(";")
                .append(lastSnapshot.terminado ? 1 : 0).append(";")
                .append(safe(lastSnapshot.mensajeFin)).append(";")
                .append(lastSnapshot.resetReadyCount).append(";");

        // estado p1
        sb.append(lastSnapshot.p1.y).append(";")
                .append(lastSnapshot.p1.enPiso   ? 1 : 0).append(";")
                .append(lastSnapshot.p1.agachado ? 1 : 0).append(";")
                .append(lastSnapshot.p1.vivo     ? 1 : 0).append(";");

        // estado p2
        sb.append(lastSnapshot.p2.y).append(";")
                .append(lastSnapshot.p2.enPiso   ? 1 : 0).append(";")
                .append(lastSnapshot.p2.agachado ? 1 : 0).append(";")
                .append(lastSnapshot.p2.vivo     ? 1 : 0).append(";");

        // obstaculos pista 1
        sb.append(lastSnapshot.obstaculosP1.size()).append(";");
        for (SnapshotDino.ObstacleState o : lastSnapshot.obstaculosP1) {
            sb.append(o.type).append(";")
                    .append(o.variant).append(";")
                    .append(o.x).append(";")
                    .append(o.y).append(";");
        }

        // obstaculos pista 2
        sb.append(lastSnapshot.obstaculosP2.size()).append(";");
        for (SnapshotDino.ObstacleState o : lastSnapshot.obstaculosP2) {
            sb.append(o.type).append(";")
                    .append(o.variant).append(";")
                    .append(o.x).append(";")
                    .append(o.y).append(";");
        }

        broadcast(sb.toString());
    }

    // reemplaza ";" dentro de strings para que no rompa el split al parsear
    private String safe(String s) {
        if (s == null) return "";
        return s.replace(";", ",");
    }

    //cuenta cuantos clientes estan listos para reiniciar
    private int resetReadyCount() {
        int c = 0;
        for (int i = 0; i < MAX_CLIENTES; i++) if (resetReady[i]) c++;
        return c;
    }

    //envia un mensaje a un cliente especifico
    private void enviar(String msg, InetAddress ip, int port) {
        try {
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(data, data.length, ip, port));
        } catch (Exception ignored) {}
    }

    //envia un mensaje a todos los clientes conectados
    private void broadcast(String msg) {
        for (int i = 0; i < cantClientes; i++) {
            enviar(msg, clientesIP[i], clientesPort[i]);
        }
    }

    //busca el indice del cliente segun su ip y puerto
    private int indexCliente(InetAddress ip, int port) {
        for (int i = 0; i < cantClientes; i++) {
            if (clientesIP[i].equals(ip) && clientesPort[i] == port) return i;
        }
        return -1;
    }

    //getters
    public SnapshotDino getLastSnapshot()  { return lastSnapshot; }
    public int getCantClientes()           { return cantClientes; }
    public boolean isPartidaIniciada()     { return partidaIniciada; }
    public void cerrar()                   { running = false; }
}