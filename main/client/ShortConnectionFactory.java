/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Una ShortConnectionFactory offre metodi per la creazione di oggetti ShortConnection con lo stesso hostname e porta.
 */
public class ShortConnectionFactory {

    private InetAddress serverAddress;
    private int serverPort;

    /**
     * Crea una nuova ShortConnectionFactory configurata con hostname e porta specificati.
     *
     * @param serverHostname l'hostname che verrà usato nelle ShortConnection create da this
     * @param serverPort     la porta che verrà usata nelle ShortConnection create da this
     * @throws UnknownHostException
     */
    public ShortConnectionFactory(String serverHostname, int serverPort) throws UnknownHostException {
        this.serverAddress = InetAddress.getByName(serverHostname);
        this.serverPort = serverPort;
    }

    /**
     * Crea un nuovo oggetto ShortConnection.
     *
     * @return un oggetto ShortConnection
     * @throws IOException
     */
    public ShortConnection makeConnection() throws IOException {
        return new ShortConnection(serverAddress, serverPort);
    }

    /**
     * Restituisce l'indirizzo col quale è stata configurata la factory.
     *
     * @return l'indirizzo
     */
    public InetAddress getServerAddress() {
        return serverAddress;
    }


}
