/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package client;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Un FriendRequestsReceiverTask si mette in ascolto su un socket per ricevere le richieste di amicizia inoltrate da un
 * server.
 */
public class FriendRequestsReceiverTask implements Runnable, Closeable {

    private Client.ChildTaskState taskState = Client.ChildTaskState.UNKNOWN;
    private ServerSocket requestsSocket;
    private final List<String> pendingFriendRequests;
    private int listeningPort = -1;
    private final Client client;
    private boolean closed = false;

    public FriendRequestsReceiverTask(List<String> pendingFriendRequests, Client client) {
        this.pendingFriendRequests = pendingFriendRequests;
        this.client = client;
    }

    @Override
    public void run() {
        try {
            requestsSocket = new ServerSocket(0);
            listeningPort = requestsSocket.getLocalPort();
            synchronized (this) {
                taskState = Client.ChildTaskState.LISTENING;
                this.notifyAll();
            }
            startLoop();
        } catch (IOException e) {
            e.printStackTrace();
            synchronized (this) {
                taskState = Client.ChildTaskState.ERROR;
                this.notifyAll();
            }
        }
    }

    private void startLoop() {
        while (true) {
            try {
                Socket connection = requestsSocket.accept();
                BufferedInputStream buffInputStream = new BufferedInputStream(connection.getInputStream());
                byte[] data = new byte[256];
                int bytes = buffInputStream.read(data);
                if (bytes > 0) {
                    String username = new String(data, 0, bytes, StandardCharsets.UTF_8);
                    synchronized (pendingFriendRequests) {
                        if (!pendingFriendRequests.contains(username))
                            pendingFriendRequests.add(username);
                    }
                    if (client.getClientEventListener() != null)
                        client.getClientEventListener().friendRequestReceived();
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

    /**
     * Restituisce la porta sulla quale l'oggetto si è messo in ascolto di richieste di amicizia, oppure -1 se non è
     * stato possibile creare il server socket.
     *
     * @return una porta oppure -1
     */
    public int getListeningPort() {
        return listeningPort;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

}
