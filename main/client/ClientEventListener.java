/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package client;

/**
 * Un'interfaccia listener per ricevere notifiche di eventi che si verificano su un client.
 */
public interface ClientEventListener {

    void friendRequestReceived();

    void friendPostReceived();

}
