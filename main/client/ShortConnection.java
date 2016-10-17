/*
 * Copyright (c) Giorgio Vinciguerra 5/2016.
 */

package client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class ShortConnection implements Closeable {

    private Socket socket;
    private BufferedInputStream bufferedInputStream;
    private BufferedOutputStream bufferedOutputStream;

    public ShortConnection(InetAddress host, int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), (int) TimeUnit.SECONDS.toMillis(5));
        socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(5));
    }

    public BufferedInputStream getBufferedInputStream() throws IOException {
        if (bufferedInputStream == null)
            bufferedInputStream = new BufferedInputStream(socket.getInputStream());
        return bufferedInputStream;
    }

    public BufferedOutputStream getBufferedOutputStream() throws IOException {
        if (bufferedOutputStream == null)
            bufferedOutputStream = new BufferedOutputStream(socket.getOutputStream());

        return bufferedOutputStream;
    }

    @Override
    public void close() throws IOException {
        if (bufferedInputStream != null)
            bufferedInputStream.close();
        if (bufferedOutputStream != null)
            bufferedOutputStream.close();
        socket.close();
    }
}
