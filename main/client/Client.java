/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package client;

import server.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Un oggetto Client, tramite una connessione TCP, inoltra a un Simple-Social server le richieste di un utente della
 * rete sociale. L'utente è identificato tramite un AuthenticationManager.
 */
public final class Client extends UnicastRemoteObject implements RemoteNotificationReceiver, Closeable {

    private String username;
    private static final long serialVersionUID = 1L;
    private transient ClientEventListener clientEventListener;
    private final transient AuthenticationManager authenticationManager;
    private final transient KeepAliveResponderTask keepAliveResponderTask;
    private final transient RemoteNotificationSender remoteNotificationSender;
    private final transient FriendRequestsReceiverTask friendRequestsReceiverTask;
    private final transient List<PostWithAuthor> unreadPosts = new ArrayList<>();
    private final transient List<String> pendingFriendRequests = new ArrayList<>();

    public enum ChildTaskState {
        UNKNOWN,
        LISTENING,
        ERROR
    }

    /**
     * Crea un nuovo oggetto Client, avvia i thread ausiliari, quindi effettua il primo login e registra una callback
     * sul server. I thread ausiliari ricevono richieste di amicizia e rispondono ai messaggi di keep-alive. La callback
     * viene eseguita quando un utente che si segue pubblica nuovi contenuti.
     *
     * @param authenticationManager l'AuthenticationManager, non null
     * @throws IOException       se ci sono problemi nella creazione dei thread ausiliari o
     * @throws ResponseException se l'username e la password sono errati
     * @throws NotBoundException se ci sono problemi nella registrazione della callback
     */
    public Client(AuthenticationManager authenticationManager) throws IOException, ResponseException, NotBoundException {
        if (authenticationManager == null)
            throw new IllegalArgumentException();

        this.remoteNotificationSender = (RemoteNotificationSender) Naming.lookup(NotificationManager.SERVICE_NAME);
        this.authenticationManager = authenticationManager;
        this.username = authenticationManager.getUsername();
        this.keepAliveResponderTask = new KeepAliveResponderTask(authenticationManager);
        this.friendRequestsReceiverTask = new FriendRequestsReceiverTask(pendingFriendRequests, this);

        // Avvia e attende i thread ausiliari
        Thread t1 = new Thread(keepAliveResponderTask);
        Thread t2 = new Thread(friendRequestsReceiverTask);
        t1.start();
        t2.start();
        try {
            synchronized (keepAliveResponderTask) {
                while (keepAliveResponderTask.getTaskState() == ChildTaskState.UNKNOWN)
                    keepAliveResponderTask.wait();
            }
            synchronized (friendRequestsReceiverTask) {
                while (friendRequestsReceiverTask.getTaskState() == ChildTaskState.UNKNOWN)
                    friendRequestsReceiverTask.wait();
            }
        } catch (InterruptedException e) {

        }
        if (keepAliveResponderTask.getTaskState() != ChildTaskState.LISTENING) {
            keepAliveResponderTask.close();
            friendRequestsReceiverTask.close();
            throw new IOException("Can't launch KeepAliveResponderTask");
        }
        if (friendRequestsReceiverTask.getTaskState() != ChildTaskState.LISTENING) {
            keepAliveResponderTask.close();
            friendRequestsReceiverTask.close();
            throw new IOException("Can't launch FriendRequestsReceiverTask");
        }

        authenticationManager.setListeningPort(friendRequestsReceiverTask.getListeningPort());
        authenticationManager.login();

        try {
            remoteNotificationSender.registerReceiver(this);
        } catch (OperationNotPermittedException e) {

        }
    }

    /**
     * Cerca gli utenti della rete che hanno una certa stringa nello username.
     *
     * @param query la stringa da cercare
     * @return una lista di nomi di utenti che hanno query nel nome
     * @throws IOException
     * @throws IllegalArgumentException se query è null o vuota
     * @throws ResponseException        se ci sono problemi di autenticazione
     * @see ServerTask#findUser()
     */
    public List<String> findUsers(String query) throws IOException, ResponseException {
        if (query == null || query.isEmpty())
            throw new IllegalArgumentException();

        ShortConnection connection = authenticationManager.makeAuthenticatedConnection(RequestTypes.FIND_USER);
        BufferedOutputStream outStream = connection.getBufferedOutputStream();
        outStream.write(query.getBytes(StandardCharsets.UTF_8));
        outStream.flush();

        ArrayList<String> results = new ArrayList<>();
        BufferedInputStream inStream = connection.getBufferedInputStream();
        Scanner scanner = new Scanner(inStream, StandardCharsets.UTF_8.name()).useDelimiter("\n");
        while (scanner.hasNext())
            results.add(scanner.next());
        connection.close();

        return results;
    }

    @Override
    public void close() {
        try {
            keepAliveResponderTask.close();
            friendRequestsReceiverTask.close();
        } catch (Exception e) {

        }
        try {
            authenticationManager.logout();
            UnicastRemoteObject.unexportObject(this, false);
        } catch (Exception e) {

        }
    }

    /**
     * Una classe immutabile che incapsula il risultato di una richiesta {@link #retrieveFriends()}.
     */
    public static final class FriendWithStatus {
        private final String username;
        private final boolean online;

        private FriendWithStatus(String username, boolean online) {
            this.username = username;
            this.online = online;
        }

        public String getUsername() {
            return username;
        }

        public boolean isOnline() {
            return online;
        }
    }

    /**
     * Restituisce la lista degli amici dell'utente col loro stato online.
     *
     * @return una lista di {@link FriendWithStatus}
     * @throws IOException
     * @throws ResponseException se ci sono problemi di autenticazione
     * @see ServerTask#sendFriends()
     */
    public List<FriendWithStatus> retrieveFriends() throws IOException, ResponseException {
        ShortConnection connection = authenticationManager.makeAuthenticatedConnection(RequestTypes.GET_FRIENDS);
        connection.getBufferedOutputStream().flush();

        ArrayList<FriendWithStatus> results = new ArrayList<>();
        BufferedInputStream inStream = connection.getBufferedInputStream();
        Scanner scanner = new Scanner(inStream, StandardCharsets.UTF_8.name()).useDelimiter("\n");
        while (scanner.hasNext()) {
            String data = scanner.next();
            boolean online = data.substring(0, 1).equals("1");
            String name = data.substring(1);
            results.add(new FriendWithStatus(name, online));
        }
        connection.close();
        return results;
    }

    /**
     * Effettua una richiesta di pubblicazione di un post.
     *
     * @throws IOException
     * @throws IllegalArgumentException se content è null o vuoto
     * @throws ResponseException        se ci sono problemi di autenticazione
     * @see ServerTask#publish()
     */
    public void publish(String content) throws IOException, ResponseException {
        if (content == null || content.isEmpty())
            throw new IllegalArgumentException();

        ShortConnection connection = authenticationManager.makeAuthenticatedConnection(RequestTypes.PUBLISH);
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        connection.getBufferedOutputStream().write(data);
        connection.getBufferedOutputStream().flush();
        connection.close();
    }

    /**
     * Registra l'interesse del Client per i contenuti di un utente.
     *
     * @param username l'utente da seguire
     * @throws RemoteException
     * @see NotificationManager#registerReceiver(RemoteNotificationReceiver, String)
     */
    public void subscribe(String username) throws RemoteException, OperationNotPermittedException {
        remoteNotificationSender.registerReceiver(this, username);
    }

    /**
     * Una classe immutabile che incapsula il risultato di una richiesta {@link #retrieveUnreadPosts()}.
     */
    public static class PostWithAuthor {
        private final String content;
        private final String author;

        private PostWithAuthor(String author, String content) {
            this.author = author;
            this.content = content;
        }

        public String getAuthor() {
            return author;
        }

        public String getContent() {
            return content;
        }
    }

    /**
     * Svuota e restituisce una lista di Post non letti, scritti da amici che si seguono (v. {@link
     * #subscribe(String)}.
     *
     * @return una lista di post non letti
     */
    public synchronized List<PostWithAuthor> retrieveUnreadPosts() {
        List<PostWithAuthor> p = new ArrayList<>(unreadPosts);
        unreadPosts.clear();
        return p;
    }


    /**
     * Svuota e restituisce una lista di richieste di amicizia non ancora confermate.
     *
     * @return una lista di nomi di utenti che hanno fatto richiesta di amicizia
     */
    public List<String> retrievePendingFriendRequests() {
        List<String> p;
        synchronized (pendingFriendRequests) {
            p = new ArrayList<>(pendingFriendRequests);
            pendingFriendRequests.clear();
        }
        return p;
    }

    /**
     * Chiede al server di inoltrare una richiesta di amicizia a un certo utente.
     *
     * @param username il nome dell'utente a cui chiedere l'amicizia
     * @throws IOException
     * @throws IllegalArgumentException se username è null o vuoto
     * @throws ResponseException        se il server risponde che username non esiste, è già un amico, o è offline
     * @see ServerTask#forwardFriendRequest()
     */
    public void friendRequest(String username) throws IOException, ResponseException {
        if (username == null || username.isEmpty())
            throw new IllegalArgumentException();

        ShortConnection connection = authenticationManager.makeAuthenticatedConnection(RequestTypes.FORWARD_FRIEND_REQUEST);
        byte[] data = username.getBytes(StandardCharsets.UTF_8);
        connection.getBufferedOutputStream().write(data);
        connection.getBufferedOutputStream().flush();

        int response = connection.getBufferedInputStream().read();
        switch (response) {
            case ResponseTypes.OK:
                return;
            case ResponseTypes.BAD_REQUEST:
                throw new ResponseException("User " + username + " is already a friend");
            case ResponseTypes.USER_OFFLINE:
                throw new ResponseException("User " + username + " offline");
            case ResponseTypes.USER_NOT_FOUND:
                throw new ResponseException("User " + username + " not found");
            default:
                throw new ResponseException();
        }
    }

    /**
     * Invia al server la risposta per una richiesta di amicizia ricevuta.
     *
     * @param username lo username dell'utente da cui si è ricevuta la richiesta di amicizia
     * @param accept   true se la richiesta viene accettata, false altrimenti
     * @throws IOException
     * @throws IllegalArgumentException se username è null o vuoto
     * @throws ResponseException        se la richiesta di amicizia non esisteva/è scaduta, o username non esiste
     * @see ServerTask#respondFriendRequest(boolean)
     */
    public void respondFriendRequest(String username, boolean accept) throws IOException, ResponseException {
        if (username == null || username.isEmpty())
            throw new IllegalArgumentException();

        byte rt = (accept ? RequestTypes.ACCEPT_FRIEND_REQUEST : RequestTypes.DENY_FRIEND_REQUEST);
        ShortConnection connection = authenticationManager.makeAuthenticatedConnection(rt);
        byte[] data = username.getBytes(StandardCharsets.UTF_8);
        connection.getBufferedOutputStream().write(data);
        connection.getBufferedOutputStream().flush();

        int response = connection.getBufferedInputStream().read();
        switch (response) {
            case ResponseTypes.OK:
                return;
            case ResponseTypes.USER_NOT_FOUND:
                throw new ResponseException("User " + username + " not found");
            case ResponseTypes.BAD_REQUEST:
                throw new ResponseException("Friend request not found");
            default:
                throw new ResponseException();
        }
    }

    /**
     * Restituisce l'oggetto che viene notificato quando avvengono nuovi eventi che riguardano il Client.
     *
     * @return un listener
     */
    public ClientEventListener getClientEventListener() {
        return clientEventListener;
    }

    /**
     * Imposta l'oggetto che verrà notificato quando avvengono nuovi eventi che riguardano il Client.
     *
     * @param clientEventListener il listener
     */
    public void setClientEventListener(ClientEventListener clientEventListener) {
        this.clientEventListener = clientEventListener;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public synchronized void notifyPost(String author, String content) throws RemoteException {
        unreadPosts.add(new PostWithAuthor(author, content));
        if (clientEventListener != null)
            clientEventListener.friendPostReceived();
    }

    @Override
    public byte[] getToken() throws RemoteException {
        return authenticationManager.getToken();
    }

}
