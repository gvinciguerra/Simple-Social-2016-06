/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package server;

import socialnetwork.User;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Date;

public class Session {

    private User user;
    private byte[] token;
    private Date startDate;
    private Date lastActionDate;
    private InetSocketAddress userAddress;
    public final static int TOKEN_BYTES = Integer.BYTES;

    /**
     * Crea una nuova sessione per l'utente specificato.
     *
     * @param user l'utente
     */
    public Session(User user) {
        this(user, null);
    }

    /**
     * Crea una nuova sessione per l'utente specificato, con associato un indirizzo.
     *
     * @param user        l'utente
     * @param userAddress l'indirizzo dell'utente oppure null
     */
    public Session(User user, InetSocketAddress userAddress) {
        if (user == null)
            throw new IllegalArgumentException();

        this.user = user;
        this.userAddress = userAddress;
        this.token = ByteBuffer.allocate(TOKEN_BYTES).putInt(this.hashCode() + (int) (Math.random() * 1000000)).array();
        this.startDate = this.lastActionDate = new Date();
    }

    /**
     * Restituisce la data dell'ultima attività dell'utente.
     *
     * @return la data dell'ultima attività
     */
    public Date getLastActionDate() {
        return new Date(lastActionDate.getTime());
    }

    /**
     * Imposta la data dell'ultima attività dell'utente.
     *
     * @param lastActionDate la data dell'ultima attività
     */
    public void setLastActionDate(Date lastActionDate) {
        this.lastActionDate = new Date(lastActionDate.getTime());
    }

    /**
     * Restituisce il token associato alla sessione.
     *
     * @return il token
     */
    public byte[] getToken() {
        return token.clone();
    }

    /**
     * Restituisce la data di creazione della sessione.
     *
     * @return la data di creazione della sessione
     */
    public Date getStartDate() {
        return new Date(startDate.getTime());
    }

    /**
     * Restituisce l'utente associato alla sessione.
     *
     * @return l'utente
     */
    public User getUser() {
        return user;
    }


    /**
     * Restituisce l'indirizzo associato all'utente della sessione, oppure null se non ne esiste uno.
     *
     * @return un indirizzo
     */
    public InetSocketAddress getUserAddress() {
        return userAddress;
    }

    /**
     * Imposta l'indirizzo associato all'utente della sessione.
     *
     * @param userAddress l'indirizzo da associare
     */
    public void setUserAddress(InetSocketAddress userAddress) {
        this.userAddress = userAddress;
    }
}