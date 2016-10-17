/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package client;

import server.Server;
import server.Session;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * Un KeepAliveResponderTask riceve e risponde ai messaggi di keep-alive. Le richieste di keep-alive vengono fatte su un
 * gruppo multicast; le risposte (che contengono il token) avvengono tramite una connessione UDP diretta col server.
 */
public class KeepAliveResponderTask implements Runnable, Closeable {

    private AuthenticationManager authenticationManager;
    private DatagramSocket responseSocket;
    private MulticastSocket requestSocket;
    private InetAddress serverAddress;
    private Client.ChildTaskState taskState = Client.ChildTaskState.UNKNOWN;
    private boolean closed = false;

    public KeepAliveResponderTask(AuthenticationManager authenticationManager) {
        if (authenticationManager == null)
            throw new IllegalArgumentException();
        this.authenticationManager = authenticationManager;
    }

    @Override
    public void run() {
        try {
            InetAddress multicastAddress = InetAddress.getByName(Server.KEEP_ALIVE_REQUEST_MULTICAST_ADDRESS);
            serverAddress = authenticationManager.getConnectionFactory().getServerAddress();
            responseSocket = new DatagramSocket();
            requestSocket = new MulticastSocket(Server.KEEP_ALIVE_REQUEST_PORT);
            requestSocket.setReuseAddress(true);
            requestSocket.joinGroup(multicastAddress);

            synchronized (this) {
                taskState = Client.ChildTaskState.LISTENING;
                this.notifyAll();
            }

            startLoop();
        } catch (Exception e) {
            synchronized (this) {
                taskState = Client.ChildTaskState.ERROR;
                this.notifyAll();
            }
        }

    }

    private void startLoop() {
        byte[] buff = new byte[Session.TOKEN_BYTES];
        DatagramPacket response = new DatagramPacket(buff, buff.length, serverAddress, Server.KEEP_ALIVE_RESPONSE_PORT);
        DatagramPacket request = new DatagramPacket(new byte[1], 1);

        while (true) {
            try {
                requestSocket.receive(request);
                byte[] token = authenticationManager.getToken();
                if (request.getData()[0] == '?' && token != null) {
                    response.setData(token);
                    responseSocket.send(response);
                }
            } catch (Exception e) {
                if (closed)
                    break;
            }
        }

    }

    public Client.ChildTaskState getTaskState() {
        return taskState;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        requestSocket.close();
        responseSocket.close();
    }
}
