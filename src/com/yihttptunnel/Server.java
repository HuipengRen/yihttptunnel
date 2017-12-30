package com.yihttptunnel;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());

    public static void main(String[] args) throws Exception {
        int httpsPort = 443;
        int httpPort = 80;

        // A https proxy in China, you can find another one from
        // http://spys.one/free-proxy-list/CN/
        String httpsProxyHost = "118.114.77.47";
        int httpsProxyPort = 8080;

        // Use api.xiaoyi.com as the http proxy directly since they don't do region lock
        // with the http traffic. But you can use a http proxy in China.
        String httpProxyHost = "139.129.76.123";
        int httpProxyPort = 80;

        // api.xiaoyi.com
        String targetHost = "139.129.76.123";
        int targetHttpsPort = 443;
        int targetHttpPort = 80;

        if (args.length == 2) {
            httpsProxyHost = args[0];
            httpsProxyPort = Integer.parseInt(args[1]);
        }

        new Server(httpPort,
                   httpProxyHost,
                   httpProxyPort,
                   targetHttpPort,
                   httpsPort,
                   httpsProxyHost,
                   httpsProxyPort,
                   targetHttpsPort,
                   targetHost,
                   100,
                   2).start();
    }

    // HTTP configurations
    private final int httpPort;
    private final String httpProxyHost;
    private final int httpProxyPort;
    private final int targetHttpPort;

    // HTTPS configurations
    private final int httpsPort;
    private final String httpsProxyHost;
    private final int httpsProxyPort;
    private final int targetHttpsPort;

    private final String targetHost;

    private final ExecutorService connectionPool;
    private final ExecutorService tunnelServicePool;

    public Server(final int httpPort,
                  final String httpProxyHost,
                  final int httpProxyPort,
                  final int targetHttpPort,
                  final int httpsPort,
                  final String httpsProxyHost,
                  final int httpsProxyPort,
                  final int targetHttpsPort,
                  final String targetHost,
                  final int maxConnection,
                  final int maxTunnelService) {

        this.httpPort = httpPort;
        this.httpProxyHost = httpProxyHost;
        this.httpProxyPort = httpProxyPort;
        this.targetHttpPort = targetHttpPort;

        this.httpsPort = httpsPort;
        this.httpsProxyHost = httpsProxyHost;
        this.httpsProxyPort = httpsProxyPort;
        this.targetHttpsPort = targetHttpsPort;

        this.targetHost = targetHost;
        this.connectionPool = Executors.newFixedThreadPool(maxConnection);
        this.tunnelServicePool = Executors.newFixedThreadPool(maxTunnelService);
    }

    public void startTunneling(final int port,
                               final String proxyHost,
                               final int proxyPort,
                               final String targetHost,
                               final int targetPort,
                               final boolean isHttps) {
        try (final ServerSocket welcomeSocket = new ServerSocket(port)) {
            logger.log(Level.INFO, "Tunnel service is running for"
                                 + "\r\nport: " + port
                                 + "\r\nproxy host: " + proxyHost
                                 + "\r\nproxy port: " + proxyPort
                                 + "\r\ntarget host: " + targetHost
                                 + "\r\ntarget port: " + targetPort
                                 + "\r\nHTTPS: " + isHttps);

            while (true) {
                final Socket clientSideSocket = welcomeSocket.accept();
                this.connectionPool.submit(() -> new Tunnel(clientSideSocket,
                                                            proxyHost,
                                                            proxyPort,
                                                            targetHost,
                                                            targetPort,
                                                            isHttps).connect());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Start tunneling exception: ", e);
        }
    }

    public void start() throws InterruptedException, ExecutionException {
        final Future<?> httpTunnelFut = this.tunnelServicePool.submit(
                () -> this.startTunneling(this.httpPort,
                                          this.httpProxyHost,
                                          this.httpProxyPort,
                                          this.targetHost,
                                          this.targetHttpPort,
                                          false));
        final Future<?> httpsTunnelFut = this.tunnelServicePool.submit(
                () -> this.startTunneling(this.httpsPort,
                                          this.httpsProxyHost,
                                          this.httpsProxyPort,
                                          this.targetHost,
                                          this.targetHttpsPort,
                                          true));

        httpTunnelFut.get();
        httpsTunnelFut.get();

        this.tunnelServicePool.shutdown();
        this.connectionPool.shutdown();
    }
}
