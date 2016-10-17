/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package server;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteNotificationReceiver extends Remote {

    /**
     * Un metodo invocato dal server per notificare l'avvenuta pubblicazione di nuovi contenuti
     *
     * @param author  l'autore del post
     * @param content il contenuto del post
     * @throws RemoteException
     */
    void notifyPost(String author, String content) throws RemoteException;

    /**
     * Restituisce il token dell'utente che vuole essere notificato di nuovi contenuti.
     *
     * @return il token
     */
    byte[] getToken() throws RemoteException;

}
