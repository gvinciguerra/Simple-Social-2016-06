/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package socialnetwork;

import java.io.Serializable;
import java.util.*;

public class User implements Serializable {

    private static final long serialVersionUID = 1L;
    private Set<User> friends;
    private Set<User> followers;
    private List<Post> posts;
    private final String username;
    private final String password;

    /**
     * Crea un nuovo utente con nome e password specificati.
     *
     * @param username il nome dell'utente
     * @param password la password dell'utente
     * @throws IllegalArgumentException se uno degli argomenti è null
     */
    public User(String username, String password) {
        if (username == null || password == null)
            throw new IllegalArgumentException();
        this.username = username;
        this.password = password;
        this.friends = new HashSet<>();
        this.followers = new HashSet<>();
        this.posts = new ArrayList<>();
    }

    public String getUsername() {
        return this.username;
    }

    public String getPassword() {
        return this.password;
    }

    /**
     * Ritorna la collezione degli amici di questo utente.
     *
     * @return una collezione non modificabile di User
     */
    public Collection<User> getFriends() {
        return Collections.unmodifiableCollection(friends);
    }

    /**
     * Aggiunge user alla collezione degli amici di questo utente. Non fa nulla se user è già presente.
     *
     * @param user l'amico da aggiungere a this
     */
    void addFriend(User user) {
        friends.add(user);
    }

    /**
     * Registra l'interesse di user per i contenuti di questo utente.
     *
     * @param user l'utente che vuole seguire this
     */
    void addFollower(User user) {
        followers.add(user);
    }

    /**
     * Ritorna la collezione degli utenti interessati ai contenuti di questo utente.
     *
     * @return una collezione non modificabile di User
     */
    public Collection<User> getFollowers() {
        return Collections.unmodifiableCollection(followers);
    }

    /**
     * Aggiunge un elemento alla lista dei contenuti pubblicati dall'utente.
     *
     * @param content il contenuto del post
     * @return l'oggetto Post appena creato
     */
    Post addPost(String content) {
        Post p = new Post(this, new Date(), content);
        posts.add(p);
        return p;
    }

    /**
     * Restituisce la lista di tutti i contenuti pubblicati dall'utente.
     *
     * @return lista non modificabile dei Post dell'utente
     */
    public List<Post> getPosts() {
        return Collections.unmodifiableList(posts);
    }

}