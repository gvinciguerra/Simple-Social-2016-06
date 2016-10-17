/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package client;

import server.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Un AuthenticationManager gestisce la connessione di un singolo utente in un Simple-Social server. In particolare si
 * occupa della registrazione e del login di un utente, nonché della richiesta di nuovi token quando questi scadono.
 * Offre un metodo per la creazione di connessioni autenticate {@link #makeAuthenticatedConnection(byte)}
 */
public class AuthenticationManager {

    private byte[] token = new byte[Session.TOKEN_BYTES];
    private Date tokenDate;
    private String username;
    private String password;
    private ShortConnectionFactory connectionFactory;
    private int listeningPort = -1;
    private int attempts;

    /**
     * Crea un nuovo AuthenticationManager.
     *
     * @param factory  la factory di ShortConnection
     * @param username lo username dell'utente
     * @param password la password dell'utente
     * @throws IllegalArgumentException se uno degli argomenti è null
     */
    public AuthenticationManager(ShortConnectionFactory factory, String username, String password)
            throws IllegalArgumentException {
        if (factory == null || username == null || password == null)
            throw new IllegalArgumentException();
        this.connectionFactory = factory;
        this.username = username;
        this.password = password;
    }

    /**
     * Restituisce il token corrente. Ne richiede uno nuovo se è scaduto o è stato effettuato il logout.
     *
     * @return il token oppure null se ci sono problemi di comunicazione col server
     */
    public byte[] getToken() {
        if (token == null || tokenDate == null || System.currentTimeMillis() - tokenDate.getTime() > SessionsManager
                .DEFAULT_SESSION_DURATION)
            try {
                login();
            } catch (Exception e) {
                return null;
            }
        return token.clone();
    }

    /**
     * Invia una richiesta di registrazione al Simple-Social server.
     *
     * @param factory  la factory di ShortConnection
     * @param username lo username da registrare
     * @param password la password da registrare
     * @throws IOException       se c'è un problema di comunicazione col server
     * @throws ResponseException se il server ha risposto che un utente con quel nome esiste
     */
    public static void register(ShortConnectionFactory factory, String username, String password)
            throws IOException, ResponseException {
        ShortConnection s = factory.makeConnection();
        BufferedOutputStream outStream = s.getBufferedOutputStream();
        outStream.write(RequestTypes.REGISTER);
        outStream.write(username.getBytes(StandardCharsets.UTF_8));
        outStream.write('\n');
        outStream.write(password.getBytes(StandardCharsets.UTF_8));
        outStream.flush();
        BufferedInputStream inStream = s.getBufferedInputStream();

        int serverResponse = inStream.read();
        switch (serverResponse) {
            case ResponseTypes.OK:
                break;
            case ResponseTypes.INVALID_CREDENTIALS:
                throw new ResponseException("Username already exists");
            default:
                throw new ResponseException();
        }
    }

    /**
     * Effettua il login e aggiorna il token.
     *
     * @throws IOException       se c'è un problema di comunicazione col server
     * @throws ResponseException se l'username e la password sono errati
     * @see ServerTask#login()
     */
    public void login() throws IOException, ResponseException {
        ShortConnection s = connectionFactory.makeConnection();
        BufferedOutputStream outStream = s.getBufferedOutputStream();
        outStream.write(RequestTypes.LOGIN);
        outStream.write(new byte[]{(byte) listeningPort, (byte) (listeningPort >> 8)});
        outStream.write(username.getBytes(StandardCharsets.UTF_8));
        outStream.write('\n');
        outStream.write(password.getBytes(StandardCharsets.UTF_8));
        outStream.flush();
        BufferedInputStream inStream = s.getBufferedInputStream();

        int serverResponse = inStream.read();
        switch (serverResponse) {
            case ResponseTypes.INVALID_CREDENTIALS:
                throw new ResponseException("Invalid credentials");
            case ResponseTypes.OK:
                byte[] tokenBuffer = new byte[Session.TOKEN_BYTES];
                if (Session.TOKEN_BYTES == inStream.read(tokenBuffer, 0, tokenBuffer.length)) {
                    token = tokenBuffer;
                    tokenDate = new Date();
                }
                break;
            default:
                token = null;
                throw new ResponseException();
        }
        attempts = 0;
    }

    /**
     * Invia una richiesta di logout al server, invalida il token corrente se la richiesta va a buon fine.
     *
     * @throws IOException
     * @see ServerTask#logout()
     */
    public void logout() throws IOException, ResponseException {
        ShortConnection s = makeAuthenticatedConnection(RequestTypes.LOGOUT);
        if (s.getBufferedInputStream().read() == ResponseTypes.OK)
            token = null;
    }

    public ShortConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * Crea una nuova richiesta del tipo specificato: apre una connessione col server, scrive request sul buffer e
     * scrive il token (rinnovandolo se necessario).
     *
     * @param request il tipo di richiesta
     * @return la ShortConnection appena creata
     * @throws IOException
     * @throws ResponseException se ci sono problemi di autenticazione
     */
    public ShortConnection makeAuthenticatedConnection(byte request) throws IOException, ResponseException {
        ShortConnection s = connectionFactory.makeConnection();
        s.getBufferedOutputStream().write(request);
        s.getBufferedOutputStream().write(getToken());
        s.getBufferedOutputStream().flush();

        int serverResponse = s.getBufferedInputStream().read();
        switch (serverResponse) {
            case ResponseTypes.OK:
                return s;
            case ResponseTypes.INVALID_TOKEN:
                if (attempts < 2) {
                    attempts++;
                    login();
                    return makeAuthenticatedConnection(request);
                } else
                    throw new ResponseException("Invalid token");
            default:
                throw new ResponseException();
        }
    }

    /**
     * Restituisce lo username associato all'AuthenticationManager.
     *
     * @return lo username
     */
    public String getUsername() {
        return username;
    }


    /**
     * Imposta la porta che verrà inviata ad ogni messaggio di login.
     *
     * @param listeningPort la porta
     */
    public void setListeningPort(int listeningPort) {
        this.listeningPort = listeningPort;
    }
}
