package io.nucleo.net.node;

import com.msopentech.thali.java.toronionproxy.JavaOnionProxyContext;
import com.msopentech.thali.java.toronionproxy.JavaOnionProxyManager;
import io.nucleo.net.*;
import io.nucleo.net.Node.Server;
import io.nucleo.net.proto.ContainerMessage;
import io.nucleo.net.proto.exceptions.ConnectionException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.regex.Pattern;

public class NodeTest {
    private static boolean running;

    static Connection currentCon = null;

    static class Listener implements ConnectionListener {
        @Override
        public void onMessage(Connection con, ContainerMessage msg) {
            System.err.println("RXD: " + msg.getPayload().toString() + " < " + con.getPeer());

        }

        @Override
        public void onDisconnect(Connection con, DisconnectReason reason) {
            if (con.equals(currentCon))
                currentCon = null;
            System.err.println(con.getPeer() + " has disconnected: " + reason.toString());

        }

        @Override
        public void onError(Connection con, ConnectionException e) {
            System.err.println("Connection " + con.getPeer() + ": " + e.getMessage());
            e.printStackTrace();

        }

        @Override
        public void onReady(Connection con) {
            System.err.println(con.getPeer() + " is ready");
            currentCon = con;

        }

    }

    public static void main(String[] args) throws InstantiationException, IOException {
        if ((args.length != 2) && (args.length != 1)) {
            System.err.println("1 or 2 params required: service port, or hidden service dir + port");
            return;
        }
        final Node node;
        final ArrayList<ConnectionListener> listener = new ArrayList<>(1);
        listener.add(new Listener());
        if (args.length == 2) {
            File dir = new File(args[0]);
            dir.mkdirs();
            TorNode<JavaOnionProxyManager, JavaOnionProxyContext> tor = new TorNode<JavaOnionProxyManager, JavaOnionProxyContext>(
                    dir) {
            };

            node = new Node(tor.createHiddenService(Integer.parseInt(args[1])), tor);
        } else {
            node = new Node(new TCPServiceDescriptor("localhost", Integer.parseInt(args[0])));
        }

        final Server server = node.startListening(new ServerConnectListener() {
            @Override
            public void onConnect(Connection con) {
                con.addMessageListener(listener.get(0));
                try {
                    con.listen();
                } catch (ConnectionException e) {
                    // never happens
                }
                System.out.println("Connection to " + con.getPeer() + " established :-)");

            }
        });
        running = true;
        Scanner scan = new Scanner(System.in);
        System.out.println("READY!");
        String line = null;
        System.out.print("\n" + node.getLocalName() + " >");
        while (running && ((line = scan.nextLine()) != null)) {
            String[] cmd = {line};
            if (line.contains(" "))
                cmd = line.split(Pattern.quote(" "));

            switch (cmd[0]) {
                case "con":
                    if (cmd.length == 2) {
                        String host = cmd[1];
                        try {
                            node.connect(host, listener);
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }
                    }
                    break;
                case "list":
                    int i = 0;
                    for (Connection con : new LinkedList<>(node.getConnections())) {
                        System.out.println("\t" + (i++) + " " + con.getPeer());
                    }
                    break;
                case "sel":
                    try {
                        if (cmd.length == 2) {
                            int index = Integer.parseInt(cmd[1]);
                            currentCon = new LinkedList<>(node.getConnections()).get(index);
                        }
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                    }
                    break;
                case "send":
                    try {
                        if (cmd.length >= 2) {
                            if (currentCon != null) {
                                currentCon.sendMessage(new ContainerMessage(line.substring(4)));
                            } else
                                System.err.println("NO node active!");
                        }
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                    }
                    break;
                case "end":
                    server.shutdown();
                    break;
                default:
                    break;
            }
            System.out.print("\n" + node.getLocalName() + ":" + (currentCon == null ? "" : currentCon.getPeer()) + " >");
        }

    }

}
