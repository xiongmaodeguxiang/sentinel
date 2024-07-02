package com.alibaba.csp.sentinel.cluster;

public class ClusterServerConfig {
    private String serverIP;
    private int port;

    public String getServerIP() {
        return serverIP;
    }

    public void setServerIP(String serverIP) {
        this.serverIP = serverIP;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

}
