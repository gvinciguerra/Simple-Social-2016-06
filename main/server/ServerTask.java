/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package server;

import socialnetwork.Post;
import socialnetwork.User;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Un ServerTask è una componente del server che legge e risponde alle richieste di un singolo client.
 */
public class ServerTask implements Runnable {

    private Server server;
    private Socket socket;
    private BufferedInputStream buffInputStream;
    private BufferedOutputStream buffOutputStream;

    public ServerTask(Server server, Socket socket) {
        this.server = server;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(5));
            buffInputStream = new BufferedInputStream(socket.getInputStream());
            buffOutputStream = new BufferedOutputStream(socket.getOutputStream());
            int action = buffInputStream.read();
            switch (action) {
                case RequestTypes.LOGIN:
                    login();
                    break;
                case RequestTypes.LOGOUT:
                    logout();
                    break;
                case RequestTypes.REGISTER:
                    register();
                    break;
                case RequestTypes.FIND_USER:
                    findUser();
                    break;
                case RequestTypes.GET_FRIENDS:
                    sendFriends();
                    break;
                case RequestTypes.PUBLISH:
                    publish();
                    break;
                case RequestTypes.FORWARD_FRIEND_REQUEST:
                    forwardFriendRequest();
                    break;
                case RequestTypes.ACCEPT_FRIEND_REQUEST:
                    respondFriendRequest(true);
                    break;
                case RequestTypes.DENY_FRIEND_REQUEST:
                    respondFriendRequest(false);
                    break;
                default:
                    break;
            }
        } catch (IOException e) {

        } finally {
            if (!socket.isClosed())
                try {
                    socket.close();
                } catch (Exception e) {

                }
        }
    }

    /**
     * Gestisce una richiesta di login. Si aspetta di ricevere la porta di ascolto di richieste di amicizia (2 byte),
     * quindi nome + '\n' + password. Risponde con OK e un token o con INVALID_CREDENTIALS.
     *
     * @throws IOException
     */
    private void login() throws IOException {
        byte[] portData = new byte[2];
        byte[] loginData = new byte[1024];
        int bytes1 = buffInputStream.read(portData);
        int bytes2 = buffInputStream.read(loginData);

        if (bytes1 == 2 && bytes2 > 0) {
            String[] login = new String(loginData, 0, bytes2, StandardCharsets.UTF_8).split("\n", 2);
            if (login.length == 2) {
                User u = server.getUsersNetwork().getUser(login[0]);
                if (u == null || !u.getPassword().equals(login[1]))
                    sendQuickResponse(ResponseTypes.INVALID_CREDENTIALS);
                else {
                    byte[] token = server.getSessionsManager().login(u);
                    buffOutputStream.write(new byte[]{ResponseTypes.OK});
                    buffOutputStream.write(token);
                    buffOutputStream.flush();
                    int port = portData[0] & 0xFF | (portData[1] << 8) & 0xFF00;
                    InetSocketAddress sa = new InetSocketAddress(socket.getInetAddress(), port);
                    server.getSessionsManager().getSession(u).setUserAddress(sa);
                }
            }
        }
    }

    /**
     * Gestisce una richiesta di logout. Si aspetta di ricevere un token. Verifica il token, quindi rimuove la sessione
     * e scrive OK sull'output stream.
     *
     * @throws IOException
     */
    private void logout() throws IOException {
        Session s = validateToken();
        if (s == null)
            return;

        server.getSessionsManager().logout(s.getToken());
        sendQuickResponse(ResponseTypes.OK);
    }

    /**
     * Gestisce una richiesta di registrazione. Si aspetta di ricevere nome e password, separati da '\n'. Scrive OK
     * sull'output stream se l'utente è stato registrato oppure INVALID_CREDENTIALS se un utente con quel nome già
     * esiste.
     *
     * @throws IOException
     */
    private void register() throws IOException {
        byte[] data = new byte[256];
        int bytes = buffInputStream.read(data);

        if (bytes > 0) {
            String[] loginData = new String(data, 0, bytes, StandardCharsets.UTF_8).split("\n", 2);
            if (loginData.length == 2) {
                if (null == server.getUsersNetwork().addUser(loginData[0], loginData[1]))
                    sendQuickResponse(ResponseTypes.INVALID_CREDENTIALS);
                else {
                    sendQuickResponse(ResponseTypes.OK);
                    server.log("[INFO] New user: " + loginData[0]);
                    server.setUsersNetworkDidChange();
                }
            }
        }
    }

    /**
     * Gestisce una richiesta di ricerca. Si aspetta di ricevere un token, seguito da una stringa di ricerca. Verifica
     * il token, quindi scrive OK e una sequenza di nomi utente separati da '\n' sull'output stream.
     *
     * @throws IOException
     */
    private void findUser() throws IOException {
        if (null == validateToken())
            return;

        byte[] data = new byte[256];
        int bytes = buffInputStream.read(data);
        if (bytes >= 0) {
            String query = new String(data, 0, bytes, StandardCharsets.UTF_8);
            String usernames = server.getUsersNetwork().findUsers(query)
                    .parallelStream()
                    .map(User::getUsername)
                    .collect(Collectors.joining("\n"));
            buffOutputStream.write(usernames.getBytes(StandardCharsets.UTF_8));
            buffOutputStream.flush();
        }
    }

    /**
     * Gestisce una richiesta di invio lista amici. Si aspetta di ricevere un token. Verifica il token, quindi scrive
     * sull'output stream una sequenza di utenti separati da '\n' e preceduti ognuno da '0' o '1' in base loro stato
     * (rispettivamente offline e online).
     *
     * @throws IOException
     */
    private void sendFriends() throws IOException {
        Session s = validateToken();
        if (s == null)
            return;

        Collection<User> activeUsers = server.getSessionsManager().getActiveUsers(10);
        String data = s.getUser().getFriends()
                .parallelStream()
                .map(u -> (activeUsers.contains(u) ? "1" : "0") + u.getUsername())
                .collect(Collectors.joining("\n"));
        buffOutputStream.write(data.getBytes(StandardCharsets.UTF_8));
        buffOutputStream.flush();
    }

    /**
     * Gestisce una richiesta di pubblicazione di contenuti. Si aspetta di ricevere un token, seguito dal contenuto del
     * post. Verifica il token, quindi registra il contenuto e scrive OK sull'output stream.
     *
     * @throws IOException
     */
    private void publish() throws IOException {
        Session s = validateToken();
        if (s == null)
            return;

        byte[] data = new byte[8192];
        int bytes = buffInputStream.read(data);
        if (bytes > 0) {
            String content = new String(data, 0, bytes, StandardCharsets.UTF_8);
            Post p = server.getUsersNetwork().addPost(s.getUser(), content);
            server.getNotificationManager().notifyPost(p);
            sendQuickResponse(ResponseTypes.OK);
            server.setUsersNetworkDidChange();
        }
    }

    /**
     * Gestisce una richiesta di amicizia. Si aspetta di ricevere un token, seguito dal nome di un utente. Verifica il
     * token, quindi prova a inoltrare la richiesta di amicizia a quell'utente. L'inoltro viene fatto contattando
     * l'utente su una porta conosciuta e sull'ultimo indirizzo con cui l'utente ha effettuato (v. {@link #login()}..
     * <p>
     * Le possibili risposte sono:
     * <p>
     * - ResponseTypes.USER_NOT_FOUND se lo username ricevuto non esiste;
     * <p>
     * - ResponseTypes.USER_OFFLINE se non è possibile inoltrare la richiesta al destinatario;
     * <p>
     * - ResponseTypes.BAD_REQUEST se gli utenti sono già amici;
     * <p>
     * - ResponseTypes.OK se la richiesta è stata inoltrata correttamente.
     *
     * @throws IOException
     */
    private void forwardFriendRequest() throws IOException {
        Session s = validateToken();
        if (s == null)
            return;

        byte[] data = new byte[256];
        int bytes = buffInputStream.read(data);
        if (bytes >= 0) {
            User receiverUser = server.getUsersNetwork().getUser(new String(data, 0, bytes, StandardCharsets.UTF_8));
            if (receiverUser == null) {
                sendQuickResponse(ResponseTypes.USER_NOT_FOUND);
                return;
            }

            Session receiverUserSession = server.getSessionsManager().getSession(receiverUser);
            if (s.getUser().getFriends().contains(receiverUser)) {
                sendQuickResponse(ResponseTypes.BAD_REQUEST);
                return;
            }
            try {
                InetSocketAddress sa = receiverUserSession.getUserAddress();
                Socket socket = new Socket(sa.getAddress(), sa.getPort());
                socket.getOutputStream().write(s.getUser().getUsername().getBytes(StandardCharsets.UTF_8));
                socket.close();
                server.getFriendRequestManager().addFriendRequest(s.getUser(), receiverUser);
                sendQuickResponse(ResponseTypes.OK);
            } catch (Exception e) {
                sendQuickResponse(ResponseTypes.USER_OFFLINE);
            }
        }
    }

    /**
     * Accetta o nega una richiesta di amicizia. Si aspetta di ricevere un token, seguito dal nome dell'utente che ha
     * inviato la richiesta. Verifica il token e risponde con OK se la richiesta esisteva e non è scaduta. Altrimenti
     * risponde con BAD_REQUEST.
     *
     * @param yesOrNo true se la richiesta di amicizia deve essere accettata, false se deve essere rifiutata
     * @throws IOException
     */
    private void respondFriendRequest(boolean yesOrNo) throws IOException {
        Session s = validateToken();
        if (s == null)
            return;

        byte[] data = new byte[256];
        int bytes = buffInputStream.read(data);
        if (bytes >= 0) {
            User sender = server.getUsersNetwork().getUser(new String(data, 0, bytes, StandardCharsets.UTF_8));
            User receiver = s.getUser();
            if (sender == null)
                sendQuickResponse(ResponseTypes.USER_NOT_FOUND);
            else {
                server.getFriendRequestManager().removeRequestsOlderThan(receiver, Server.MAX_FRIEND_REQUEST_LIFE);
                boolean found = server.getFriendRequestManager().confirmFriendRequest(sender, receiver, yesOrNo);
                sendQuickResponse(found ? ResponseTypes.OK : ResponseTypes.BAD_REQUEST);
                if (found)
                    server.setUsersNetworkDidChange();
            }
        }
    }

    /**
     * Legge un token e verifica che sia valido, quindi scrive OK sull'output stream. Se il token non è valido risponde
     * con INVALID_TOKEN.
     *
     * @return la sessione associata al token se questo è valido ed è stato scritto OK sull'output stream, null
     * altrimenti
     * @throws IOException
     */
    private Session validateToken() throws IOException {
        byte[] token = new byte[Session.TOKEN_BYTES];
        if (Session.TOKEN_BYTES == buffInputStream.read(token)) {
            Session session = server.getSessionsManager().getSession(token);
            if (session == null)
                sendQuickResponse(ResponseTypes.INVALID_TOKEN);
            else {
                sendQuickResponse(ResponseTypes.OK);
                session.setLastActionDate(new Date());
                return session;
            }
        }
        return null;
    }

    private void sendQuickResponse(byte response) throws IOException {
        buffOutputStream.write(new byte[]{response});
        buffOutputStream.flush();
    }

}
