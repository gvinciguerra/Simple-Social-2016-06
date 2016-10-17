/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package socialnetwork;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;


/**
 * Una UsersNetwork rappresenta una rete di utenti con alcuni vincoli come l'univocità dei nomi, la relazione di
 * amicizia simmetrica e quella di iscrizione ai contenuti asimmetrica.
 */
public class UsersNetwork implements Serializable {

    private static final long serialVersionUID = 1L;
    private final Collection<User> users = new HashSet<>();
    private final Map<String, User> usersMap = new HashMap<>();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    public UsersNetwork() {

    }

    /**
     * Crea un utente con nome e password specificati e lo aggiunge alla rete. Restituisce l'utente appena creato oppure
     * null se un utente con quel nome già esiste nella rete.
     *
     * @param username il nome dell'utente
     * @param password la password dell'utente
     * @return l'utente appena creato oppure null
     */
    public User addUser(String username, String password) {
        readWriteLock.writeLock().lock();
        try {
            if (getUser(username) != null)
                return null;
            User u = new User(username, password);
            users.add(u);
            usersMap.put(username, u);
            return u;
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Trova e restituisce un utente col nome specificato. Se un utente con quel nome non è registrato alla rete
     * restituisce null.
     *
     * @param username il nome dell'utente da cercare
     * @return l'utente col nome specificato oppure null
     */
    public User getUser(String username) {
        readWriteLock.readLock().lock();
        try {
            return usersMap.get(username);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Registra un'amicizia tra due utenti nella rete.
     *
     * @throws UserNotFoundException se uno dei due utenti non appertiene alla rete
     */
    public void addFriendship(User user1, User user2) throws UserNotFoundException {
        readWriteLock.writeLock().lock();
        try {
            if (!users.contains(user1) || !users.contains(user2))
                throw new UserNotFoundException();
            user1.addFriend(user2);
            user2.addFriend(user1);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Registra un nuovo post con autore e contenuto forniti.
     *
     * @param author  l'autore del post
     * @param content il contenuto del post
     * @return il post appena creato
     * @throws UserNotFoundException se author non appartiene alla rete
     */
    public Post addPost(User author, String content) throws UserNotFoundException {
        readWriteLock.writeLock().lock();
        try {
            if (!users.contains(author))
                throw new UserNotFoundException();
            return author.addPost(content);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Restituisce la collezione degli utenti registrati alla rete.
     *
     * @return una collezione non modificabile degli utenti
     */
    public Collection<User> getUsers() {
        readWriteLock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(users);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Registra l'intenzione di user1 di seguire i contenuti pubblicati da user2. Non fa nulla se i due utenti non sono
     * amici.
     *
     * @param user1 l'utente che vuole seguire
     * @param user2 l'utente che viene seguito
     * @throws UserNotFoundException se uno dei due utenti non appertiene alla rete
     */
    public void addSubscription(User user1, User user2) throws UserNotFoundException {
        readWriteLock.writeLock().lock();
        try {
            if (!users.contains(user1) || !users.contains(user2))
                throw new UserNotFoundException();
            if (user1.getFriends().contains(user2))
                user2.addFollower(user1);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Cerca tra gli utenti registrati nella rete quelli che contentgono una certa stringa nello username.
     *
     * @param query il nome da cercare
     * @return la collezione dei risultati
     */
    public Collection<User> findUsers(String query) {
        readWriteLock.readLock().lock();
        try {
            if (query == null)
                return getUsers();
            return users.stream().filter(u -> u.getUsername().contains(query)).collect(Collectors.toList());
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

}