package com.dinosauriojuego.net;

import java.net.InetAddress;
//guarda la ip y el puerto de un cliente para que el sv sepa donde mandarle los msjs
public class DireccionRed {
    private final InetAddress ip;
    private final int puerto;

    public DireccionRed(InetAddress ip, int puerto) {
        this.ip = ip;
        this.puerto = puerto;
    }

    public InetAddress getIp() { return ip; }
    public int getPuerto() { return puerto; }
}
