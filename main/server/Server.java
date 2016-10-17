/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package server;

import socialnetwork.FriendRequestsManager;
import socialnetwork.UsersNetwork;

import java.io.*;
import java.net.*;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Server implements Closeable {

    // Logica del programma
    private NotificationManager notificationManager;
    private UsersNetwork usersNetwork = new UsersNetwork();
    private SessionsManager sessionsManager = new SessionsManager();
    private FriendRequestsManager friendRequestManager;
    private TimerTask keepAliveTimerTask;
    private TimerTask backupTask;
    private final PrintStream console;
    private boolean closed;
    private boolean usersNetworkDidChange;
    private final boolean backupEnabled;
    public static final long MAX_FRIEND_REQUEST_LIFE = TimeUnit.DAYS.toMillis(3);

    // Connessione di rete
    private ServerSocket serverSocket;
    private InetAddress multicastAddress;
    private MulticastSocket keepAliveRequestSocket;
    private DatagramSocket keepAliveResponseSocket;
    public static final int SERVER_PORT = 11234;
    public static final int KEEP_ALIVE_TIME = 10000;
    public static final int KEEP_ALIVE_RESPONSE_PORT = 11236;
    public static final int KEEP_ALIVE_REQUEST_PORT = 11235;
    public static final String KEEP_ALIVE_REQUEST_MULTICAST_ADDRESS = "239.255.123.43";

    /**
     * Crea un nuovo oggetto Server con un PrintStream personalizzato su cui verranno scritti i messaggi di log.
     *
     * @param console       il PrintStream su cui verranno scritti i messaggi di log, può essere null
     * @param backupEnabled true se devono essere salvati e ripristinati i backup
     */
    public Server(PrintStream console, boolean backupEnabled) {
        try {
            multicastAddress = InetAddress.getByName(KEEP_ALIVE_REQUEST_MULTICAST_ADDRESS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.backupEnabled = backupEnabled;
        this.console = console;
    }

    void log(String s) {
        if (console != null)
            console.println(s);
    }

    UsersNetwork getUsersNetwork() {
        return usersNetwork;
    }

    NotificationManager getNotificationManager() {
        return notificationManager;
    }

    SessionsManager getSessionsManager() {
        return sessionsManager;
    }

    FriendRequestsManager getFriendRequestManager() {
        return friendRequestManager;
    }

    /**
     * Avvia un timer periodico che invia un messaggio di keep-alive su un gruppo multicast. Avvia un thread che ascolta
     * su una connessione UDP le risposte a tale messaggio da parte dei client e ne aggiorna di conseguenza lo stato.
     *
     * @throws IOException
     */
    private void startKeepAliveTask() throws IOException {
        // Avvia il timer periodico
        keepAliveRequestSocket = new MulticastSocket();
        DatagramPacket keepAlivePacket = new DatagramPacket(new byte[]{'?'}, 0, 1, multicastAddress, KEEP_ALIVE_REQUEST_PORT);
        keepAliveTimerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    keepAliveRequestSocket.send(keepAlivePacket);
                } catch (Exception e) {

                }
            }
        };
        new Timer().scheduleAtFixedRate(keepAliveTimerTask, KEEP_ALIVE_TIME, KEEP_ALIVE_TIME);

        // Avvia il thread che ascolta le risposte
        keepAliveResponseSocket = new DatagramSocket(null);
        keepAliveResponseSocket.setReuseAddress(true);
        keepAliveResponseSocket.bind(new InetSocketAddress("localhost", KEEP_ALIVE_RESPONSE_PORT));
        DatagramPacket packet = new DatagramPacket(new byte[Session.TOKEN_BYTES], Session.TOKEN_BYTES);

        new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        keepAliveResponseSocket.receive(packet);
                        if (packet.getLength() == Session.TOKEN_BYTES) {
                            byte[] token = packet.getData();
                            Session s = sessionsManager.getSession(token);
                            if (s != null)
                                s.setLastActionDate(new Date());
                        }
                    } catch (Exception e) {
                        if (closed)
                            break;
                    }
                }
            }
        }.start();
    }

    /**
     * Avvia un timer che periodicamente effettua il backup della rete sociale quando ci sono cambiamenti, quindi prova
     * a ripristinare un backup esistente.
     *
     * @return true se è stato ripristinato un backup
     */
    private boolean startBackupTask() {
        if (!backupEnabled)
            return false;

        boolean loaded = false;
        final String backupPath = "usersNetwork.ssbk";
        if (new File(backupPath).exists())
            try {
                FileInputStream fileInputStream = new FileInputStream(backupPath);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                UsersNetwork temp = (UsersNetwork) objectInputStream.readObject();
                if (temp != null) {
                    usersNetwork = temp;
                    loaded = true;
                }
            } catch (IOException | ClassNotFoundException e) {
                log("[ERROR] Restoring a backup: " + e.getMessage());
            }

        backupTask = new TimerTask() {
            @Override
            public void run() {
                if (!usersNetworkDidChange)
                    return;
                try {
                    FileOutputStream fout = new FileOutputStream(backupPath);
                    ObjectOutputStream oos = new ObjectOutputStream(fout);
                    oos.writeObject(usersNetwork);
                    oos.close();
                    fout.close();
                    usersNetworkDidChange = false;
                } catch (IOException e) {

                }
            }
        };
        new Timer().scheduleAtFixedRate(backupTask, TimeUnit.SECONDS.toMillis(30), TimeUnit.MINUTES.toMillis(2));

        return loaded;
    }

    private void startLoop() {
        closed = false;
        int processors = Runtime.getRuntime().availableProcessors();
        ExecutorService threadPool = new ThreadPoolExecutor(processors, processors * 25, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                threadPool.submit(new ServerTask(this, socket));
            } catch (IOException e) {
                if (closed)
                    break;
                else
                    log("[ERROR] New connection: " + e.getMessage());
            }
        }
        threadPool.shutdown();
    }

    public void startServer() throws IOException {
        serverSocket = new ServerSocket(SERVER_PORT);
        log("[INFO] Server started");
        startKeepAliveTask();
        log("[INFO] Keep-alive component started");
        notificationManager = new NotificationManager(this);
        log("[INFO] NotificationManager component started");
        if (startBackupTask())
            log("[INFO] Backup loaded (" + usersNetwork.getUsers().size() + " users)");
        friendRequestManager = new FriendRequestsManager(usersNetwork);
        startLoop();
    }

    public void close() {
        closed = true;
        try {
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
            if (keepAliveTimerTask != null)
                keepAliveTimerTask.cancel();
            if (keepAliveRequestSocket != null)
                keepAliveRequestSocket.close();
            if (keepAliveResponseSocket != null)
                keepAliveResponseSocket.close();
            if (backupTask != null) {
                backupTask.cancel();
                backupTask.run();
            }
        } catch (IOException e) {

        }
        log("[INFO] Server closed");
    }

    /**
     * Informa il server del cambiamento della rete sociale.
     *
     * @see #startBackupTask()
     */
    void setUsersNetworkDidChange() {
        this.usersNetworkDidChange = true;
    }

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        Server s = new Server(System.out, true);
        try {
            s.startServer();
        } catch (IOException e) {
            System.out.println("Il server non può essere avviato: " + e.getLocalizedMessage());
        }
    }

}