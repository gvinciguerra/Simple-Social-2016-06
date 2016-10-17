/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package socialnetwork;

import java.util.*;

/**
 * Un FriendRequestsManager si occupa di gestire le richieste di amicizia di uno UsersNetwork. Offre metodi per la
 * creazione/conferma di richieste di amicizia, e per la rimozione di richieste non confermate entro un certo periodo.
 */
public class FriendRequestsManager {

    private UsersNetwork usersNetwork;
    private final Map<User, List<ExpirableFriendRequest>> requests = new HashMap<>();

    private static class ExpirableFriendRequest {
        final User user;
        final Date date;

        ExpirableFriendRequest(User user) {
            this.user = user;
            this.date = new Date();
        }
    }

    public FriendRequestsManager(UsersNetwork usersNetwork) {
        if (usersNetwork == null)
            throw new IllegalArgumentException();
        this.usersNetwork = usersNetwork;
    }

    /**
     * Aggiunge una richiesta di amicizia o ne rinnova una esistente. Non fa nulla se i due utenti sono già amici.
     *
     * @param user1 l'utente che chiede l'amicizia
     * @param user2 l'utente che dovrà accettare la richiesta
     * @throws UserNotFoundException se uno dei due utenti non esiste nella rete sociale
     */
    public synchronized void addFriendRequest(User user1, User user2) {
        if (!usersNetwork.getUsers().contains(user1) || !usersNetwork.getUsers().contains(user2))
            throw new UserNotFoundException();
        if (user1.getFriends().contains(user2))
            return;
        requests.putIfAbsent(user2, new ArrayList<>());
        requests.get(user2).removeIf(e -> e.user.equals(user1));
        requests.get(user2).add(new ExpirableFriendRequest(user1));
    }

    /**
     * Conferma/nega una richiesta di amicizia precedentemente inserita.
     *
     * @param user1   l'utente che ha chiesto l'amicizia
     * @param user2   l'utente che ha accettato l'amicizia
     * @param confirm true se la richiesta deve essere confermata, false se deve essere negata
     * @return false se e solo se la richiesta di amicizia è scaduta o non è mai stata richiesta
     */
    public synchronized boolean confirmFriendRequest(User user1, User user2, boolean confirm) {
        List<ExpirableFriendRequest> user2requests = requests.get(user2);
        if (user2requests == null)
            return false;

        Optional<ExpirableFriendRequest> fr = user2requests.stream().filter(f -> f.user == user1).findFirst();
        if (!fr.isPresent())
            return false;

        user2requests.remove(fr.get());
        if (!confirm)
            return true;

        try {
            usersNetwork.addFriendship(user1, user2);
            return true;
        } catch (UserNotFoundException e) {
            return false;
        }
    }

    /**
     * Rimuove tutte le richieste di amicizia ricevute da un utente più vecchie di un certo tempo.
     *
     * @param user   l'utente
     * @param millis i millisecondi oltre i quali una richiesta viene cancellata
     */
    public synchronized void removeRequestsOlderThan(User user, long millis) {
        long now = System.currentTimeMillis();
        List<ExpirableFriendRequest> userRequests = requests.get(user);

        Iterator<ExpirableFriendRequest> iterator = userRequests.iterator();
        while (iterator.hasNext())
            if (now - iterator.next().date.getTime() > millis)
                iterator.remove();
    }
}
