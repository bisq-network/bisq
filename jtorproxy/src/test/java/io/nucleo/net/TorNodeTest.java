package io.nucleo.net;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class TorNodeTest {

    private static final int hsPort = 55555;
    private static CountDownLatch serverLatch = new CountDownLatch(1);

    private static TorNode<JavaOnionProxyManager, JavaOnionProxyContext> node;

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException, InstantiationException {
        File dir = new File("tor-test");
        dir.mkdirs();
        for (String str : args)
            System.out.print(str + " ");
        node = new TorNode<JavaOnionProxyManager, JavaOnionProxyContext>(dir) {
        };
        final ServiceDescriptor hiddenService = node.createHiddenService(hsPort);
        new Thread(new Server(hiddenService.getServerSocket())).start();
        serverLatch.await();

        if (args.length != 2)
            new Client(node.connectToHiddenService(hiddenService.getHostname(), hiddenService.getServicePort())).run();
        else {
            System.out.println("\nHs Running, pres return to connect to " + args[0] + ":" + args[1]);
            final Scanner scanner = new Scanner(System.in);
            scanner.nextLine();
            new Client(node.connectToHiddenService(args[0], Integer.parseInt(args[1])), scanner).run();
        }

        // node.shutdown();
    }

    private static class Client implements Runnable {

        private Socket sock;
        private final Scanner scanner;

        private Client(Socket sock, Scanner scanner) {
            this.sock = sock;
            this.scanner = scanner;
        }

        private Client(Socket sock) {
            this(sock, new Scanner(System.in));
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                OutputStreamWriter out = new OutputStreamWriter(sock.getOutputStream());
                System.out.print("\n> ");
                String input = scanner.nextLine();
                out.write(input + "\n");
                out.flush();
                String aLine = null;
                while ((aLine = in.readLine()) != null) {
                    System.out.println(aLine);
                    System.out.print("\n> ");
                    input = scanner.nextLine();
                    out.write(input + "\n");
                    out.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private static class Server implements Runnable {
        private final ServerSocket socket;

        private Server(ServerSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            System.out.println("Wating for incoming connections...");
            serverLatch.countDown();
            try {
                while (true) {

                    Socket sock = socket.accept();
                    System.out.println("Accepting Client " + sock.getRemoteSocketAddress() + " on port " + sock.getLocalPort());
                    BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                    OutputStreamWriter out = new OutputStreamWriter(sock.getOutputStream());
                    String aLine = null;
                    while ((aLine = in.readLine()) != null) {
                        System.out.println("ECHOING " + aLine);
                        out.write("ECHO " + aLine + "\n");
                        out.flush();
                        if (aLine.equals("END"))
                            break;
                    }
                    sock.close();
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

    }

}
