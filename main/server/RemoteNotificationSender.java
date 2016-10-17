/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package server;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteNotificationSender extends Remote {

    /**
     * Registra l'interesse di receiver per i contenuti di un amico e una callback che verrà eseguita quando un
     * qualsiasi amico che receiver segue pubblica nuovi contenuti.
     *
     * @param receiver l'oggetto che deve essere notificato di nuovi contenuti
     * @param username il nome dell'amico che si vuole seguire
     * @throws RemoteException
     * @throws OperationNotPermittedException se receiver.getToken() non è valido o se l'utente col nome specificato non
     *                                        esiste o non è amico di receiver
     */
    void registerReceiver(RemoteNotificationReceiver receiver, String username) throws RemoteException,
            OperationNotPermittedException;

    /**
     * Registra una callback che verrà eseguita quando un qualsiasi amico che receiver segue pubblica nuovi contenuti.
     *
     * @param receiver l'oggetto che deve essere notificato di nuovi contenuti
     * @throws RemoteException
     * @throws OperationNotPermittedException se receiver.getToken() non è valido
     */
    void registerReceiver(RemoteNotificationReceiver receiver) throws RemoteException, OperationNotPermittedException;

}
