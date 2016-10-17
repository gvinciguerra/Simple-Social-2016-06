/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package server;

import socialnetwork.Post;
import socialnetwork.User;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Un NotificationManager si occupa di invocare una callback registrata da utenti che vogliono essere avvisati quando un
 * loro amico pubblica un Post.
 */
public class NotificationManager extends UnicastRemoteObject implements RemoteNotificationSender {

    private static final long serialVersionUID = 1L;
    private final transient Server server;
    private final transient Map<User, RemoteNotificationReceiver> allReceivers = new ConcurrentHashMap<>();
    private final transient Map<User, Collection<Post>> allUnsentPosts = new ConcurrentHashMap<>();
    public static final String SERVICE_NAME = "simpleSocialNotificationService";

    /**
     * Crea un NotificationManager, se non esiste avvia un registro RMI, quindi vi salva un'associazione tra
     * SERVICE_NAME e this.
     *
     * @param server l'oggetto server
     * @throws RemoteException
     */
    public NotificationManager(Server server) throws RemoteException {
        this.server = server;
        try {
            LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
        } catch (Exception e) {

        } finally {
            LocateRegistry.getRegistry(Registry.REGISTRY_PORT).rebind(SERVICE_NAME, this);
        }
    }

    @Override
    public void registerReceiver(RemoteNotificationReceiver receiver) throws RemoteException, OperationNotPermittedException {
        Session receiverSession = server.getSessionsManager().getSession(receiver.getToken());
        if (receiverSession == null)
            throw new OperationNotPermittedException("Invalid token");

        allReceivers.put(receiverSession.getUser(), receiver);
        Collection<Post> unsentPosts = allUnsentPosts.get(receiverSession.getUser());
        if (unsentPosts == null)
            return;
        synchronized (unsentPosts) {
            for (Iterator<Post> i = unsentPosts.iterator(); i.hasNext(); ) {
                try {
                    Post post = i.next();
                    receiver.notifyPost(post.getAuthor().getUsername(), post.getContent());
                    i.remove();
                } catch (RemoteException e) {

                }
            }
        }
    }

    @Override
    public void registerReceiver(RemoteNotificationReceiver receiver, String username) throws RemoteException,
            OperationNotPermittedException {
        Session receiverSession = server.getSessionsManager().getSession(receiver.getToken());
        if (receiverSession == null)
            throw new OperationNotPermittedException("Invalid token");
        allReceivers.put(receiverSession.getUser(), receiver);

        User user2 = server.getUsersNetwork().getUser(username);
        if (user2 == null)
            throw new OperationNotPermittedException("The user doesn't exists.");
        if (!receiverSession.getUser().getFriends().contains(user2))
            throw new OperationNotPermittedException("The user is not a friend.");

        server.getUsersNetwork().addSubscription(receiverSession.getUser(), user2);
    }

    /**
     * Notifica un post a tutti gli oggetti che si sono registrati ai contenuti all'autore (v. {@link
     * #registerReceiver(RemoteNotificationReceiver, String)}.
     *
     * @param post il post da notificare
     */
    public void notifyPost(Post post) {
        Collection<User> followers = post.getAuthor().getFollowers();
        followers.parallelStream()
                .forEach(f -> {
                    try {
                        allReceivers.get(f).notifyPost(post.getAuthor().getUsername(), post.getContent());
                    } catch (Exception e) {
                        allUnsentPosts.putIfAbsent(f, new ArrayList<>());
                        allUnsentPosts.get(f).add(post);
                    }
                });
    }

}
