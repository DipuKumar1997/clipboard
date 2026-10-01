package com.wifisync.model;

import java.util.Objects;

public class Peer {
    private final String ipAddress;
    private final String hostName;

    public Peer(String ipAddress, String hostName) {
        this.ipAddress = ipAddress;
        this.hostName = hostName;
    }

    public String getIpAddress() { return ipAddress; }
    public String getHostName() { return hostName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Peer peer = (Peer) o;
        return Objects.equals(ipAddress, peer.ipAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ipAddress);
    }
}