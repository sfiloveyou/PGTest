package com.sf.pg.mapper;
/**
 * Created with IntelliJ IDEA.
 * User: che
 * Date: 20.01.13
 * Time: 9:22
 */
public class MappingInfo {
    private int localPort = -1;
    private int remotePort = -1;
    private String remoteHost = null;

    boolean isComplete() {
        return localPort() > 0 && remotePort() > 0 && remoteHost() != null;
    }

    public int localPort(){
        return localPort;
    }

    public int remotePort(){
        return remotePort;
    }

    public String remoteHost(){
        return remoteHost;
    }


    public MappingInfo localPort(int localPort){
        this.localPort = localPort;
        return this;
    }

    public MappingInfo remotePort(int remotePort){
        this.remotePort = remotePort;
        return this;
    }

    public MappingInfo remoteHost(String remoteHost){
        this.remoteHost = remoteHost;
        return this;
    }


}
