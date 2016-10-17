/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package socialnetwork;

import java.io.Serializable;
import java.util.Date;

public class Post implements Serializable {

    private static final long serialVersionUID = 1L;
    private final User author;
    private final Date date;
    private final String content;

    public Post(User author, Date date, String content) {
        this.author = author;
        this.date = date;
        this.content = content;
    }

    public User getAuthor() {
        return author;
    }

    public Date getDate() {
        return new Date(date.getTime());
    }

    public String getContent() {
        return content;
    }

}