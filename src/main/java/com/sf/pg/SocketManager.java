package com.sf.pg;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketManager {
    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) {
        SocketManager manager = new SocketManager();
        manager.doListen();
    }

    public void doListen() {
        ServerSocket server;
        try {
            server = new ServerSocket(9991);
            while (true) {
                Socket client = server.accept();
                new Thread(new SSocket(client)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //服务器进程
    class SSocket implements Runnable {

        Socket client;

        public SSocket(Socket client) {
            this.client = client;
        }

        public void run() {
            DataInputStream input;
            DataOutputStream output;
            try {
                input = new DataInputStream(client.getInputStream());
                output = new DataOutputStream(client.getOutputStream());
                System.out.println("Recive: " + input.readUTF());
                output.writeUTF("");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
} 