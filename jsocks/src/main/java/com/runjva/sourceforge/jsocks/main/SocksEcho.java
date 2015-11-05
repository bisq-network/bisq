package com.runjva.sourceforge.jsocks.main;

import com.runjva.sourceforge.jsocks.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;

public class SocksEcho extends Frame implements ActionListener, Runnable,
        WindowListener {

    Logger log = LoggerFactory.getLogger(SocksEcho.class);

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    // GUI components
    TextField host_text, port_text, input_text;
    Button proxy_button, accept_button, clear_button, connect_button,
            udp_button, quit_button;
    TextArea output_textarea;
    Label status_label;

    SocksDialog socks_dialog;

    // Network related members
    SocksProxyBase proxy = null;
    int port;
    String host;
    Thread net_thread = null;
    InputStream in = null;
    OutputStream out = null;
    Socket sock = null;
    ServerSocket server_sock = null;
    Socks5DatagramSocket udp_sock;

    Object net_lock = new Object();
    int mode = COMMAND_MODE;

    // Possible mode states.
    static final int LISTEN_MODE = 0;
    static final int CONNECT_MODE = 1;
    static final int UDP_MODE = 2;
    static final int COMMAND_MODE = 3;
    static final int ABORT_MODE = 4;

    // Maximum datagram size
    static final int MAX_DATAGRAM_SIZE = 1024;

    // Constructors
    // //////////////////////////////////
    public SocksEcho() {
        super("SocksEcho");
        guiInit();
        socks_dialog = new SocksDialog(this);
        SocksDialog.useThreads = false;

        final URL icon_url = SocksEcho.class.getResource("SocksEcho.gif");
        if (icon_url != null) {
            try {
                final Object content = icon_url.getContent();
                if (content instanceof java.awt.image.ImageProducer) {
                    setIconImage(createImage((java.awt.image.ImageProducer) content));
                }
            } catch (final IOException ioe) {
                log.debug("Could not getContent() for {}", icon_url, ioe);
            }
        }

        addWindowListener(this);
        final Component component[] = getComponents();
        for (int i = 0; i < component.length; ++i) {
            if (component[i] instanceof Button) {
                ((Button) component[i]).addActionListener(this);
            } else if (component[i] instanceof TextField) {
                ((TextField) component[i]).addActionListener(this);
            }
        }

    }

    // ActionListener interface
    // /////////////////////////
    public void actionPerformed(ActionEvent ae) {
        final Object source = ae.getSource();

        if (source == proxy_button) {
            onProxy();
        } else if (source == quit_button) {
            onQuit();
        } else if ((source == connect_button) || (source == port_text)
                || (source == host_text)) {
            onConnect();
        } else if (source == input_text) {
            onInput();
        } else if (source == accept_button) {
            onAccept();
        } else if (source == udp_button) {
            onUDP();
        } else if (source == clear_button) {
            onClear();
        }
    }

    // Runnable interface
    // /////////////////////////////

    public void run() {
        boolean finished_OK = true;
        try {
            switch (mode) {
                case UDP_MODE:
                    startUDP();
                    doUDPPipe();
                    break;
                case LISTEN_MODE:
                    doAccept();
                    doPipe();
                    break;
                case CONNECT_MODE:
                    doConnect();
                    doPipe();
                    break;
                default:
                    warn("Unexpected mode in run() method");
            }

        } catch (final UnknownHostException uh_ex) {
            if (mode != ABORT_MODE) {
                finished_OK = false;
                status("Host " + host + " has no DNS entry.");
                uh_ex.printStackTrace();
            }
        } catch (final IOException io_ex) {
            if (mode != ABORT_MODE) {
                finished_OK = false;
                status("" + io_ex);
                io_ex.printStackTrace();
            }
        } finally {
            if (mode == ABORT_MODE) {
                status("Connection closed");
            } else if (finished_OK) {
                status("Connection closed by foreign host.");
            }

            onDisconnect();
        }
    }

    // Private methods
    // //////////////////////////////////////////////////////////////////////

    // GUI event handlers.
    // ////////////////////////

    private void onConnect() {
        if (mode == CONNECT_MODE) {
            status("Diconnecting...");
            abort_connection();
            return;
        } else if (mode != COMMAND_MODE) {
            return;
        }

        if (!readHost()) {
            return;
        }
        if (!readPort()) {
            return;
        }

        if (proxy == null) {
            warn("Proxy is not set");
            onProxy();
            return;
        }

        startNetThread(CONNECT_MODE);
        status("Connecting to " + host + ":" + port + "  ...");

        connect_button.setLabel("Disconnect");
        connect_button.invalidate();
        accept_button.setEnabled(false);
        udp_button.setEnabled(false);
        doLayout();
        input_text.requestFocus();
    }

    private void onDisconnect() {
        synchronized (net_lock) {
            mode = COMMAND_MODE;
            connect_button.setLabel("Connect");
            accept_button.setLabel("Accept");
            udp_button.setLabel("UDP");
            accept_button.setEnabled(true);
            connect_button.setEnabled(true);
            udp_button.setEnabled(true);
            server_sock = null;
            sock = null;
            out = null;
            in = null;
            net_thread = null;
        }
    }

    private void onAccept() {
        if (mode == LISTEN_MODE) {
            abort_connection();
            return;
        } else if (mode != COMMAND_MODE) {
            return;
        }

        if (!readHost()) {
            return;
        }
        if (!readPort()) {
            port = 0;
        }

        if (proxy == null) {
            warn("Proxy is not set");
            onProxy();
            return;
        }

        startNetThread(LISTEN_MODE);

        accept_button.setLabel("Abort");
        connect_button.setEnabled(false);
        udp_button.setEnabled(false);
        input_text.requestFocus();
    }

    private void onUDP() {
        if (mode == UDP_MODE) {
            abort_connection();
            return;
        } else if (mode == ABORT_MODE) {
            return;
        }

        if (proxy == null) {
            warn("Proxy is not set");
            onProxy();
            return;
        }

        startNetThread(UDP_MODE);
        udp_button.setLabel("Abort");
        connect_button.setEnabled(false);
        accept_button.setEnabled(false);
        udp_button.invalidate();
        doLayout();
        input_text.requestFocus();
    }

    private void onInput() {
        final String send_string = input_text.getText() + "\n";
        switch (mode) {
            case ABORT_MODE: // Fall through
            case COMMAND_MODE:
                return;
            case CONNECT_MODE:// Fall through
            case LISTEN_MODE:
                synchronized (net_lock) {
                    if (out == null) {
                        return;
                    }
                    send(send_string);
                }
                break;
            case UDP_MODE:
                if (!readHost()) {
                    return;
                }
                if (!readPort()) {
                    return;
                }
                sendUDP(send_string, host, port);
                break;
            default:
                print("Unknown mode in onInput():" + mode);

        }
        input_text.setText("");
        print(send_string);
    }

    private void onClear() {
        output_textarea.setText("");
    }

    private void onProxy() {
        SocksProxyBase p;
        p = socks_dialog.getProxy(proxy);
        if (p != null) {
            proxy = p;
        }
        if ((proxy != null) && (proxy instanceof Socks5Proxy)) {
            ((Socks5Proxy) proxy).resolveAddrLocally(false);
        }
    }

    private void onQuit() {
        dispose();
        System.exit(0);
    }

    // Data retrieval functions
    // ////////////////////////

    /**
     * Reads the port field, returns false if parsing fails.
     */
    private boolean readPort() {
        try {
            port = Integer.parseInt(port_text.getText());
        } catch (final NumberFormatException nfe) {
            warn("Port invalid!");
            return false;
        }
        return true;
    }

    private boolean readHost() {
        host = host_text.getText();
        host.trim();
        if (host.length() < 1) {
            warn("Host is not set");
            return false;
        }
        return true;
    }

    // Display functions
    // /////////////////

    private void status(String s) {
        status_label.setText(s);
    }

    private void println(String s) {
        output_textarea.append(s + "\n");
    }

    private void print(String s) {
        output_textarea.append(s);
    }

    private void warn(String s) {
        status(s);
        // System.err.println(s);
    }

    // Network related functions
    // //////////////////////////

    private void startNetThread(int m) {
        mode = m;
        net_thread = new Thread(this);
        net_thread.start();
    }

    private void abort_connection() {
        synchronized (net_lock) {
            if (mode == COMMAND_MODE) {
                return;
            }
            mode = ABORT_MODE;
            if (net_thread != null) {
                try {
                    if (sock != null) {
                        sock.close();
                    }
                    if (server_sock != null) {
                        server_sock.close();
                    }
                    if (udp_sock != null) {
                        udp_sock.close();
                    }
                } catch (final IOException ioe) {
                    log.warn("abort_connection(): could not close socket", ioe);
                }
                net_thread.interrupt();
                net_thread = null;
            }
        }
    }

    private void doAccept() throws IOException {

        println("Trying to accept from " + host);
        status("Trying to accept from " + host);
        println("Using proxy:" + proxy);
        server_sock = new SocksServerSocket(proxy, host, port);

        // server_sock.setSoTimeout(30000);

        println("Listenning on: " + server_sock.getInetAddress() + ":"
                + server_sock.getLocalPort());
        sock = server_sock.accept();
        println("Accepted from:" + sock.getInetAddress() + ":" + sock.getPort());

        status("Accepted from:" + sock.getInetAddress().getHostAddress() + ":"
                + sock.getPort());

        server_sock.close(); // Even though this doesn't do anything
    }

    private void doConnect() throws IOException {
        println("Trying to connect to:" + host + ":" + port);
        println("Using proxy:" + proxy);
        sock = new SocksSocket(proxy, host, port);
        println("Connected to:" + sock.getInetAddress() + ":" + port);
        status("Connected to: " + sock.getInetAddress().getHostAddress() + ":"
                + port);
        println("Via-Proxy:" + sock.getLocalAddress() + ":"
                + sock.getLocalPort());

    }

    private void doPipe() throws IOException {
        out = sock.getOutputStream();
        in = sock.getInputStream();

        final byte[] buf = new byte[1024];
        int bytes_read;
        while ((bytes_read = in.read(buf)) > 0) {
            print(new String(buf, 0, bytes_read));
        }

    }

    private void startUDP() throws IOException {
        udp_sock = new Socks5DatagramSocket(proxy, 0, null);
        println("UDP started on " + udp_sock.getLocalAddress() + ":"
                + udp_sock.getLocalPort());
        status("UDP:" + udp_sock.getLocalAddress().getHostAddress() + ":"
                + udp_sock.getLocalPort());
    }

    private void doUDPPipe() throws IOException {
        final DatagramPacket dp = new DatagramPacket(
                new byte[MAX_DATAGRAM_SIZE], MAX_DATAGRAM_SIZE);
        while (true) {
            udp_sock.receive(dp);
            print("UDP\n" + "From:" + dp.getAddress() + ":" + dp.getPort()
                    + "\n" + "\n" +
                    // Java 1.2
                    // new
                    // String(dp.getData(),dp.getOffset(),dp.getLength())+"\n"
                    // Java 1.1
                    new String(dp.getData(), 0, dp.getLength()) + "\n");
            dp.setLength(MAX_DATAGRAM_SIZE);
        }
    }

    private void sendUDP(String message, String host, int port) {
        if (!udp_sock.isProxyAlive(100)) {
            status("Proxy closed connection");
            abort_connection();
            return;
        }

        try {
            final byte[] data = message.getBytes();
            final DatagramPacket dp = new DatagramPacket(data, data.length,
                    null, port);
            udp_sock.send(dp, host);
        } catch (final UnknownHostException uhe) {
            status("Host " + host + " has no DNS entry.");
        } catch (final IOException ioe) {
            status("IOException:" + ioe);
            abort_connection();
        }

    }

    private void send(String s) {
        try {
            out.write(s.getBytes());
        } catch (final IOException io_ex) {
            println("IOException:" + io_ex);
            abort_connection();
        }
    }

	/*
     * ======================================================================
	 * Form: Table: +---+---------------+ | | | +---+---+---+---+---+ | | | | |
	 * | +---+---+---+---+---+ | | +-------------------+ | |
	 * +---+---+---+---+---+ | | | | | | +---+---+---+---+---+ | |
	 * +-------------------+
	 */

    void guiInit() {
        // Some default names used
        Label label;
        Container container;

        final GridBagConstraints c = new GridBagConstraints();

        container = this;
        // container = new Panel();
        container.setLayout(new GridBagLayout());
        container.setBackground(SystemColor.menu);
        c.insets = new Insets(3, 3, 3, 3);

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHEAST;
        label = new Label("Host:");
        container.add(label, c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHEAST;
        label = new Label("Port:");
        container.add(label, c);

        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.gridheight = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 0);
        status_label = new Label("");
        container.add(status_label, c);
        c.insets = new Insets(3, 3, 3, 3);

        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        host_text = new TextField("");
        container.add(host_text, c);

        c.weightx = 1.0;
        c.fill = GridBagConstraints.NONE;
        c.gridx = 1;
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        port_text = new TextField("", 5);
        container.add(port_text, c);

        c.weightx = 0.0;
        c.gridx = 0;
        c.gridy = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        input_text = new TextField("");
        container.add(input_text, c);

        c.fill = GridBagConstraints.NONE;
        c.gridx = 2;
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHEAST;
        connect_button = new Button("Connect");
        container.add(connect_button, c);

        c.gridx = 3;
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.EAST;
        accept_button = new Button("Accept");
        container.add(accept_button, c);

        c.gridx = 4;
        c.gridy = 1;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.EAST;
        udp_button = new Button("UDP");
        container.add(udp_button, c);

        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        proxy_button = new Button("Proxy...");
        container.add(proxy_button, c);

        c.gridx = 3;
        c.gridy = 4;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHEAST;
        clear_button = new Button("Clear");
        container.add(clear_button, c);

        c.gridx = 4;
        c.gridy = 4;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.EAST;
        quit_button = new Button("Quit");
        container.add(quit_button, c);

        c.weightx = 1.0;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        output_textarea = new TextArea("", 10, 50);
        output_textarea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        output_textarea.setEditable(false);
        // output_textarea.setEnabled(false);
        container.add(output_textarea, c);

    }// end guiInit

    // WindowListener Interface
    // ///////////////////////////////
    public void windowActivated(java.awt.event.WindowEvent e) {
    }

    public void windowDeactivated(java.awt.event.WindowEvent e) {
    }

    public void windowOpened(java.awt.event.WindowEvent e) {
    }

    public void windowClosing(java.awt.event.WindowEvent e) {
        if (e.getWindow() == this) {
            onQuit();
        } else {
            e.getWindow().dispose();
        }
    }

    public void windowClosed(java.awt.event.WindowEvent e) {
    }

    public void windowIconified(java.awt.event.WindowEvent e) {
    }

    public void windowDeiconified(java.awt.event.WindowEvent e) {
    }

    // Main
    // //////////////////////////////////
    public static void main(String[] args) {
        final SocksEcho socksecho = new SocksEcho();
        socksecho.pack();
        socksecho.setVisible(true);
    }
}// end class
