/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package server;

import socialnetwork.User;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Un SessionManager permette di creare e gestire sessioni di uno User nel social network. Una sessione viene creata con
 * un {@link #login(User)}, e distrutta dopo un {@link #logout(User)} o dopo un periodo definito.
 */
public class SessionsManager {

    private long maxSessionDurationMillis;
    private Session oldestSession;
    private Timer oldestSessionTimer;
    private final Map<User, Session> sessionsMap = new HashMap<>();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    public static final long DEFAULT_SESSION_DURATION = TimeUnit.HOURS.toSeconds(24);

    /**
     * Crea un nuovo SessionsManager con la durata di sessione specificata.
     *
     * @param sessionDuration la durata di una sessione in secondi
     * @throws IllegalArgumentException se sessionDuration < 1
     */
    public SessionsManager(long sessionDuration) throws IllegalArgumentException {
        if (sessionDuration < 1)
            throw new IllegalArgumentException("sessionDuration < 1");

        maxSessionDurationMillis = TimeUnit.SECONDS.toMillis(sessionDuration);
    }

    /**
     * Crea un nuovo SessionsManager con durata di sessione impostata a DEFAULT_SESSION_DURATION.
     */
    public SessionsManager() {
        this(DEFAULT_SESSION_DURATION);
    }

    /**
     * Avvia una sessione per l'utente oppure ne aggiorna lo stato se già ne esiste una. Nel primo caso genera un nuovo
     * token/sessione, nel secondo restituisce quello ancora valido e aggiorna la data di attività dell'utente. Una
     * sessione scade: (1) dopo una chiamata a {@link #logout(User)}, oppure (2) automaticamente dopo il numero di
     * secondi specificato nel costruttore.
     *
     * @param user l'utente per il quale si vuole una sessione aperta
     * @return il token della sessione
     * @throws IllegalArgumentException se user è null
     */
    public byte[] login(User user) throws IllegalArgumentException {
        if (user == null)
            throw new IllegalArgumentException();

        readWriteLock.writeLock().lock();
        try {
            Session sessionForUser = sessionsMap.get(user);
            if (sessionForUser != null) {
                sessionForUser.setLastActionDate(new Date());
                return sessionForUser.getToken();
            }

            Session newSession = new Session(user);
            sessionsMap.put(user, newSession);
            if (sessionsMap.size() == 1)
                restartTimer();
            return newSession.getToken();
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Elimina la sessione dell'utente e invalida il token. Non fa nulla se user non ha sessioni aperte.
     *
     * @param user l'utente di cui si vuole eliminare la sessione
     * @throws IllegalArgumentException se user è null
     */
    public void logout(User user) {
        if (user == null)
            throw new IllegalArgumentException();

        readWriteLock.writeLock().lock();
        try {
            Session sessionForUser = sessionsMap.remove(user);
            if (sessionForUser != null && sessionForUser == oldestSession)
                restartTimer();
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Elimina la sessione dell'utente e invalida il token. Non fa nulla se il token non corrisponde a nessuna sessione
     * aperta.
     *
     * @param token il token della sessione da eleminare
     */
    public void logout(byte[] token) {
        Session s = getSession(token);
        if (s != null)
            logout(s.getUser());
    }

    /**
     * Ritorna una collezione di sessioni aperte, cioè sessioni per cui non è stato eseguito {@link #logout(User)} e che
     * non hanno superato la durata di sessione specificata nel costruttore.
     *
     * @return una collezione non modificabile di sessioni
     */
    public Collection<Session> getSessions() {
        readWriteLock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(sessionsMap.values());
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Ritorna la sessione corrispondente al token oppure null se non ci sono sessioni aperte con quel token.
     *
     * @param token il token da usare per cercare la sessione
     * @return la sessione corrispondente al token oppure null
     */
    public Session getSession(byte[] token) {
        readWriteLock.readLock().lock();
        try {
            Optional<Session> s = sessionsMap.values().stream()
                    .filter(o -> Arrays.equals(o.getToken(), token))
                    .findFirst();
            if (s.isPresent())
                return s.get();
            return null;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Ritorna la sessione associata all'utente oppure null se non ci sono sessioni aperte per quell'utente.
     *
     * @param user l'utente da usare per cercare la sessione
     * @return la sessione corrispondente a user oppure null
     */
    public Session getSession(User user) {
        readWriteLock.readLock().lock();
        try {
            return sessionsMap.get(user);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Trova gli utenti attivi recentemente, cioè utenti che hanno sessioni aperte e per i quali è stato invocato {@link
     * #login(User)} negli ultimi secondi.
     *
     * @param seconds i secondi con i quali filtrare la lista delle sessioni attive
     * @return una collezione di User attivi
     */
    public Collection<User> getActiveUsers(long seconds) {
        readWriteLock.readLock().lock();
        try {
            long now = System.currentTimeMillis();
            return sessionsMap.values().stream()
                    .filter(s -> now - s.getLastActionDate().getTime() < TimeUnit.SECONDS.toMillis(seconds))
                    .map(Session::getUser)
                    .collect(Collectors.toList());
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Interrompe un eventuale timer attivo e ne avvia un nuovo per la sessione aperta più vecchia. Allo scadere del
     * timer, tale sessione viene distrutta.
     */
    private void restartTimer() {
        if (oldestSessionTimer != null)
            oldestSessionTimer.cancel();

        Optional<Session> oldestSessionOpt = sessionsMap.values().stream().min(Comparator.comparing(Session::getStartDate));
        if (!oldestSessionOpt.isPresent())
            oldestSession = null;
        else {
            oldestSession = oldestSessionOpt.get();
            long oldestSessionDuration = System.currentTimeMillis() - oldestSession.getStartDate().getTime();
            long fireDelay = maxSessionDurationMillis - oldestSessionDuration;

            if (fireDelay < 0) {
                sessionsMap.remove(oldestSession.getUser());
                oldestSession = null;
                restartTimer();
            } else {
                oldestSessionTimer = new Timer();
                oldestSessionTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        readWriteLock.writeLock().lock();
                        sessionsMap.remove(oldestSession.getUser());
                        restartTimer();
                        readWriteLock.writeLock().unlock();
                    }
                }, fireDelay);
            }
        }
    }

}