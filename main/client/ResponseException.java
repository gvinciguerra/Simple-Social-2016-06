/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package client;

/**
 * Una ResponseException viene sollevata tipicamente quando un oggetto Client richiede un'operazione non valida al
 * server.
 */
public class ResponseException extends Exception {

    public ResponseException() {
        super();
    }

    public ResponseException(String s) {
        super(s);
    }
}
