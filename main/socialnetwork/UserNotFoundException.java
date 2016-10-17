/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package socialnetwork;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String msg) {
        super(msg);
    }

    public UserNotFoundException() {

    }

}
