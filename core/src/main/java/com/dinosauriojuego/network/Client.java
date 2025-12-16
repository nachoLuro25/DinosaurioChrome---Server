package com.dinosauriojuego.network;

import java.net.InetAddress;

public class Client {

    private String id;
    private int num;
    private InetAddress ip;
    private int port;

    public Client(int num, InetAddress ip, int port) {
        this.num = num;
        this.id = ip.toString() + ":" + port;
        this.ip = ip;
        this.port = port;
    }

    public String getId() {
        return this.id;
    }

    public InetAddress getIp() {
        return this.ip;
    }

    public int getPort() {
        return this.port;
    }

    public int getNum() {
        return this.num;
    }
}