package com.yihttptunnel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Tunnel {
    private static final Logger logger = Logger.getLogger(Tunnel.class.getName());

    private final Socket clientSideSocket;
    private final String proxyHost;
    private final int proxyPort;
    private final String targetHost;
    private final int targetPort;
    private final boolean isHttps;

    public Tunnel(final Socket clientSideSocket,
                  final String proxyHost,
                  final int proxyPort,
                  final String targetHost,
                  final int targetPort,
                  final boolean isHttps) {
        this.clientSideSocket = clientSideSocket;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.isHttps = isHttps;
    }

    public void connect() {
        final String clientIpPort = this.clientSideSocket.getInetAddress() + ":" + this.clientSideSocket.getPort();
        logger.log(Level.INFO, "ServerSocket accept: " + clientIpPort + ", HTTPS: " + this.isHttps);

        try (final Socket tunnel = new Socket(this.proxyHost, this.proxyPort);
             final Socket clientSocket = this.clientSideSocket) {

            if (this.isHttps) {
                doTunnelHandshake(tunnel, this.targetHost, this.targetPort, clientIpPort);
            }

            final OutputStream clientOut = clientSocket.getOutputStream();
            final InputStream clientIn = clientSocket.getInputStream();
            final OutputStream serverOut = tunnel.getOutputStream();
            final InputStream serverIn = tunnel.getInputStream();

            final Thread clientToServer = new Thread(() -> {
                logger.log(Level.INFO, "For client " + clientIpPort + " start forwarding data: client -> server");
                copyStream(clientIn, serverOut);
                logger.log(Level.INFO, "For client " + clientIpPort + " stop forwarding data: client -> server");
            });

            final Thread serverToClient = new Thread(() -> {
                logger.log(Level.INFO, "For client " + clientIpPort + " start forwarding data: server -> client");
                copyStream(serverIn, clientOut);
                logger.log(Level.INFO, "For client " + clientIpPort + " stop forwarding data: server -> client");
            });

            clientToServer.start();
            serverToClient.start();
            clientToServer.join();
            serverToClient.join();

            logger.log(Level.INFO, "For client " + clientIpPort + " tunnel is finished");
        } catch (Exception e) {
            logger.log(Level.WARNING, "For client " + clientIpPort + " tunnel connect exception: ", e);
        }
    }

    private static void copyStream(final InputStream input, final OutputStream output) {
        try {
            final byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Tunnel copy stream exception: ", e);
        }
    }

    /*
     * Tell our tunnel where we want to CONNECT, and look for the right reply. Throw
     * IOException if anything goes wrong.
     */
    private static void doTunnelHandshake(final Socket tunnel,
                                          final String targetHost,
                                          final int targetPort,
                                          final String clientIpPort) throws IOException {
        logger.log(Level.INFO, "For client " + clientIpPort + " start tunnel handshake");

        final OutputStream out = tunnel.getOutputStream();
        final String msg = "CONNECT " + targetHost + ":" + targetPort + " HTTP/1.0\n" + "Host: " + targetHost + ":"
                + targetPort + "\r\n\r\n";
        final byte b[] = msg.getBytes(StandardCharsets.US_ASCII);
        out.write(b);
        out.flush();

        final byte reply[] = new byte[200];
        int replyLen = 0;
        int newlinesSeen = 0;
        boolean headerDone = false;

        final InputStream in = tunnel.getInputStream();
        while (newlinesSeen < 2) {
            int i = in.read();
            if (i < 0) {
                throw new IOException("Unexpected EOF from proxy");
            }
            if (i == '\n') {
                headerDone = true;
                ++newlinesSeen;
            } else if (i != '\r') {
                newlinesSeen = 0;
                if (!headerDone && replyLen < reply.length) {
                    reply[replyLen++] = (byte) i;
                }
            }
        }

        final String replyStr = new String(reply, 0, replyLen, StandardCharsets.US_ASCII);
        if (!replyStr.startsWith("HTTP/1.0 200")) {
            throw new IOException("Unable to tunnel through proxy returns \"" + replyStr + "\"");
        }

        logger.log(Level.INFO, "For client " + clientIpPort + " tunnel handshake is successful");
    }
}
