package com.runjva.sourceforge.jsocks.protocol;

import java.awt.*;
import java.awt.event.*;

/**
 * Socks configuration dialog.<br>
 * Class which provides GUI means of getting Proxy configuration from the user.
 */
// FIXME: Only used by SocksEcho class

public class SocksDialog extends Dialog implements WindowListener,
        ItemListener, ActionListener, Runnable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    // GUI components
    TextField host_text, port_text, user_text, password_text, direct_text;
    Button add_button, remove_button, cancel_button, ok_button, dismiss_button;
    List direct_list;
    Checkbox socks4radio, socks5radio, none_check, up_check, gssapi_check;

    Dialog warning_dialog;
    Label warning_label;

    String host, user, password;
    int port;
    Thread net_thread = null;

    // CheckboxGroups
    CheckboxGroup socks_group = new CheckboxGroup();

    SocksProxyBase proxy;
    InetRange ir;

    final static int COMMAND_MODE = 0;
    final static int OK_MODE = 1;

    int mode;

    /**
     * Wether to resolve addresses in separate thread.
     * <p>
     * Default value is true, however on some JVMs, namely one from the
     * Microsoft, it doesn't want to work properly, separate thread can't close
     * the dialog opened in GUI thread, and everuthing else is then crashes.
     * <p>
     * When setting this variable to false, SocksDialog will block while trying
     * to look up proxy host, and if this takes considerable amount of time it
     * might be annoying to user.
     */
    public static boolean useThreads = true;

    // Constructors
    // //////////////////////////////////

    /**
     * Creates SOCKS configuration dialog.<br>
     * Uses default initialisation:<br>
     * Proxy host: socks-proxy <br>
     * Proxy port: 1080 <br>
     * Version: 5<br>
     */
    public SocksDialog(Frame parent) {
        this(parent, null);
    }

    /**
     * Creates SOCKS configuration dialog and initialises it to given proxy.
     */
    public SocksDialog(Frame parent, SocksProxyBase init_proxy) {
        super(parent, "Proxy Configuration", true);
        warning_dialog = new Dialog(parent, "Warning", true);

        guiInit();
        setResizable(false);
        addWindowListener(this);
        final Component[] comps = getComponents();
        for (int i = 0; i < comps.length; ++i) {
            if (comps[i] instanceof Button) {
                ((Button) comps[i]).addActionListener(this);
            } else if (comps[i] instanceof TextField) {
                ((TextField) comps[i]).addActionListener(this);
            } else if (comps[i] instanceof Checkbox) {
                ((Checkbox) comps[i]).addItemListener(this);
            }
        }
        proxy = init_proxy;
        if (proxy != null) {
            doInit(proxy);
        } else {
            ir = new InetRange();
        }

        dismiss_button.addActionListener(this);
        warning_dialog.addWindowListener(this);
    }

    // Public Methods
    // //////////////

    /**
     * Displays SOCKS configuartion dialog.
     * <p>
     * Returns initialised proxy object, or null if user cancels dialog by
     * either pressing Cancel or closing the dialog window.
     */
    public SocksProxyBase getProxy() {
        mode = COMMAND_MODE;
        pack();
        setVisible(true);
        return proxy;
    }

    /**
     * Initialises dialog to given proxy and displays SOCKS configuartion
     * dialog.
     * <p>
     * Returns initialised proxy object, or null if user cancels dialog by
     * either pressing Cancel or closing the dialog window.
     */
    public SocksProxyBase getProxy(SocksProxyBase p) {
        if (p != null) {
            doInit(p);
        }
        mode = COMMAND_MODE;
        pack();
        setVisible(true);
        return proxy;
    }

    // WindowListener Interface
    // ///////////////////////////////
    public void windowActivated(java.awt.event.WindowEvent e) {
    }

    public void windowDeactivated(java.awt.event.WindowEvent e) {
    }

    public void windowOpened(java.awt.event.WindowEvent e) {
    }

    public void windowClosing(java.awt.event.WindowEvent e) {
        final Window source = e.getWindow();
        if (source == this) {
            onCancel();
        } else if (source == warning_dialog) {
            onDismiss();
        }
    }

    public void windowClosed(java.awt.event.WindowEvent e) {
    }

    public void windowIconified(java.awt.event.WindowEvent e) {
    }

    public void windowDeiconified(java.awt.event.WindowEvent e) {
    }

    // ActionListener interface
    // /////////////////////////
    public void actionPerformed(ActionEvent ae) {

        final Object source = ae.getSource();

        if (source == cancel_button) {
            onCancel();
        } else if ((source == add_button) || (source == direct_text)) {
            onAdd();
        } else if (source == remove_button) {
            onRemove();
        } else if (source == dismiss_button) {
            onDismiss();
        } else if ((source == ok_button) || (source instanceof TextField)) {
            onOK();
        }
    }

    // ItemListener interface
    // //////////////////////
    public void itemStateChanged(ItemEvent ie) {
        final Object source = ie.getSource();
        // System.out.println("ItemEvent:"+source);
        if ((source == socks5radio) || (source == socks4radio)) {
            onSocksChange();
        } else if (source == up_check) {
            onUPChange();
        }

    }

    // Runnable interface
    // //////////////////

    /**
     * Resolves proxy address in other thread, to avoid annoying blocking in GUI
     * thread.
     */
    public void run() {

        if (!initProxy()) {
            // Check if we have been aborted
            if (mode != OK_MODE) {
                return;
            }
            if (net_thread != Thread.currentThread()) {
                return;
            }

            mode = COMMAND_MODE;
            warning_label.setText("Look up failed.");
            warning_label.invalidate();
            return;
        }

        // System.out.println("Done!");
        while (!warning_dialog.isShowing()) {
            ; /* do nothing */
        }
        ;

        warning_dialog.dispose();
        // dispose(); //End Dialog
    }

    // Private Methods
    // /////////////////
    private void onOK() {
        host = host_text.getText().trim();
        user = user_text.getText();
        password = password_text.getText();

        if (host.length() == 0) {
            warn("Proxy host is not set!");
            return;
        }
        if (socks_group.getSelectedCheckbox() == socks4radio) {
            if (user.length() == 0) {
                warn("User name is not set");
                return;
            }

        } else {
            if (up_check.getState()) {
                if (user.length() == 0) {
                    warn("User name is not set.");
                    return;
                }
                if (password.length() == 0) {
                    warn("Password is not set.");
                    return;
                }
            } else if (!none_check.getState()) {
                warn("Please select at least one Authentication Method.");
                return;
            }
        }

        try {
            port = Integer.parseInt(port_text.getText());
        } catch (final NumberFormatException nfe) {
            warn("Proxy port is invalid!");
            return;
        }

        mode = OK_MODE;

        if (useThreads) {
            net_thread = new Thread(this);
            net_thread.start();

            info("Looking up host: " + host);
            // System.out.println("Info returned.");
        } else if (!initProxy()) {
            warn("Proxy host is invalid.");
            mode = COMMAND_MODE;
        }

        if (mode == OK_MODE) {
            dispose();
        }
    }

    private void onCancel() {
        // System.out.println("Cancel");
        proxy = null;
        dispose();
    }

    private void onAdd() {
        final String s = direct_text.getText();
        s.trim();
        if (s.length() == 0) {
            return;
        }
        // Check for Duplicate
        final String[] direct_hosts = direct_list.getItems();
        for (int i = 0; i < direct_hosts.length; ++i) {
            if (direct_hosts[i].equals(s)) {
                return;
            }
        }

        direct_list.add(s);
        ir.add(s);
    }

    private void onRemove() {
        final int index = direct_list.getSelectedIndex();
        if (index < 0) {
            return;
        }
        ir.remove(direct_list.getItem(index));
        direct_list.remove(index);
        direct_list.select(index);
    }

    private void onSocksChange() {
        final Object selected = socks_group.getSelectedCheckbox();
        if (selected == socks4radio) {
            user_text.setEnabled(true);
            password_text.setEnabled(false);
            none_check.setEnabled(false);
            up_check.setEnabled(false);
        } else {
            if (up_check.getState()) {
                user_text.setEnabled(true);
                password_text.setEnabled(true);
            } else {
                user_text.setEnabled(false);
                password_text.setEnabled(false);
            }
            none_check.setEnabled(true);
            up_check.setEnabled(true);
        }
        // System.out.println("onSocksChange:"+selected);
    }

    private void onUPChange() {
        // System.out.println("onUPChange");
        if (up_check.getState()) {
            user_text.setEnabled(true);
            password_text.setEnabled(true);
        } else {
            user_text.setEnabled(false);
            password_text.setEnabled(false);
        }
    }

    private void onDismiss() {
        warning_dialog.dispose();
        if (mode == OK_MODE) {
            mode = COMMAND_MODE;
            if (net_thread != null) {
                net_thread.interrupt();
            }
        }
    }

    private void doInit(SocksProxyBase p) {
        if (p.version == 5) {
            socks_group.setSelectedCheckbox(socks5radio);
            onSocksChange();
            if (((Socks5Proxy) p).getAuthenticationMethod(0) != null) {
                none_check.setState(true);
            }
            final UserPasswordAuthentication auth = (UserPasswordAuthentication) ((Socks5Proxy) p)
                    .getAuthenticationMethod(2);
            if (auth != null) {
                user_text.setText(auth.getUser());
                password_text.setText(auth.getPassword());
                up_check.setState(true);
                onUPChange();
            }
        } else {
            socks_group.setSelectedCheckbox(socks4radio);
            onSocksChange();
            user_text.setText(((Socks4Proxy) p).user);
        }
        ir = (InetRange) (p.directHosts.clone());
        final String[] direct_hosts = ir.getAll();
        direct_list.removeAll();
        for (int i = 0; i < direct_hosts.length; ++i) {
            direct_list.add(direct_hosts[i]);
        }

        host_text.setText(p.proxyIP.getHostName());
        port_text.setText("" + p.proxyPort);

    }

    private boolean initProxy() {
        try {
            if (socks_group.getSelectedCheckbox() == socks5radio) {
                proxy = new Socks5Proxy(host, port);
                if (up_check.getState()) {
                    ((Socks5Proxy) proxy).setAuthenticationMethod(2,
                            new UserPasswordAuthentication(user, password));
                }
                if (!none_check.getState()) {
                    ((Socks5Proxy) proxy).setAuthenticationMethod(0, null);
                }
            } else {
                proxy = new Socks4Proxy(host, port, user);
            }
        } catch (final java.net.UnknownHostException uhe) {
            return false;
        }
        proxy.directHosts = ir;
        return true;
    }

    private void info(String s) {
        msgBox("Info", s);
    }

    private void warn(String s) {
        msgBox("Warning", s);
    }

    private void msgBox(String title, String message) {
        warning_label.setText(message);
        warning_label.invalidate();
        warning_dialog.setTitle(title);
        warning_dialog.pack();
        warning_dialog.setVisible(true);
    }

	/*
     * ======================================================================
	 * Form: Table:
	 */
    // +---+-------+---+---+
    // |...|.......|...|...|
    // +---+-------+---+---+
    // |...........|.......|
    // +---+-------+-------+
    // |...|.......|.......|
    // +---+-------+-------+
    // |...|.......|.......|
    // +---+-------+-------+
    // |...........|.......|
    // +-----------+-------+
    // |...........|.......|
    // |...........+---+---+
    // |...........|...|...|
    // +-----------+---+---+
    // |...........|...|...|
    // +---+---+---+---+---+
    // |...|...|...|...|...|
    // +---+---+---+---+---+
    //          

    void guiInit() {
        // Some default names used
        Label label;
        Container container;
        Font font;

        final GridBagConstraints c = new GridBagConstraints();

        font = new Font("Dialog", Font.PLAIN, 12);

        container = this;
        // container = new Panel();
        container.setLayout(new GridBagLayout());
        container.setFont(font);
        container.setBackground(SystemColor.menu);
        c.insets = new Insets(3, 3, 3, 3);

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHEAST;
        label = new Label("Host:");
        container.add(label, c);

        c.gridx = 1;
        c.gridy = 0;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        host_text = new TextField("socks-proxy", 15);
        container.add(host_text, c);

        c.gridx = 3;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHEAST;
        label = new Label("Port:");
        container.add(label, c);

        c.gridx = 4;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        port_text = new TextField("1080", 5);
        container.add(port_text, c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 3;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTH;
        socks4radio = new Checkbox("Socks4", socks_group, false);
        // 1.0 compatible code
        // socks4radio = new Checkbox("Socks4",false);
        // socks4radio.setCheckboxGroup(socks_group);
        socks4radio.setFont(new Font(font.getName(), Font.BOLD, 14));
        container.add(socks4radio, c);

        c.gridx = 3;
        c.gridy = 1;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTH;
        socks5radio = new Checkbox("Socks5", socks_group, true);
        // 1.0 compatible code
        // socks5radio = new Checkbox("Socks5",true);
        // socks5radio.setCheckboxGroup(socks_group);
        socks5radio.setFont(new Font(font.getName(), Font.BOLD, 14));
        container.add(socks5radio, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.EAST;
        label = new Label("User Id:");
        container.add(label, c);

        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        user_text = new TextField("", 15);
        user_text.setEnabled(false);
        container.add(user_text, c);

        c.gridx = 3;
        c.gridy = 2;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTH;
        label = new Label("Authentication");
        label.setFont(new Font(font.getName(), Font.BOLD, 14));
        container.add(label, c);

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.EAST;
        label = new Label("Password:");
        container.add(label, c);

        c.gridx = 1;
        c.gridy = 3;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        password_text = new TextField("", 15);
        password_text.setEchoChar('*');
        password_text.setEnabled(false);
        // password_text.setEchoCharacter('*');//1.0
        container.add(password_text, c);

        c.gridx = 3;
        c.gridy = 3;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        none_check = new Checkbox("None", true);
        container.add(none_check, c);

        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 3;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTH;
        label = new Label("Direct Hosts");
        label.setFont(new Font(font.getName(), Font.BOLD, 14));
        container.add(label, c);

        c.gridx = 3;
        c.gridy = 4;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        up_check = new Checkbox("User/Password", false);
        container.add(up_check, c);

        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 3;
        c.gridheight = 2;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.BOTH;
        direct_list = new List(3);
        container.add(direct_list, c);

        c.gridx = 3;
        c.gridy = 5;
        c.gridwidth = 2;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        gssapi_check = new Checkbox("GSSAPI", false);
        gssapi_check.setEnabled(false);
        container.add(gssapi_check, c);

        c.gridx = 0;
        c.gridy = 7;
        c.gridwidth = 3;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        direct_text = new TextField("", 25);
        container.add(direct_text, c);

        c.gridx = 3;
        c.gridy = 7;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTH;
        add_button = new Button("Add");
        container.add(add_button, c);

        c.gridx = 3;
        c.gridy = 6;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTH;
        remove_button = new Button("Remove");
        container.add(remove_button, c);

        c.gridx = 1;
        c.gridy = 8;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTH;
        cancel_button = new Button("Cancel");
        container.add(cancel_button, c);

        c.gridx = 0;
        c.gridy = 8;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        ok_button = new Button("OK");
        container.add(ok_button, c);

        // up_check.setEnabled(false);

        // Warning Dialog
        dismiss_button = new Button("Dismiss");
        warning_label = new Label("", Label.CENTER);
        warning_label.setFont(new Font("Dialog", Font.BOLD, 15));

        final Panel p = new Panel();
        p.add(dismiss_button);
        warning_dialog.add(p, BorderLayout.SOUTH);
        warning_dialog.add(warning_label, BorderLayout.CENTER);
        warning_dialog.setResizable(false);
    }// end guiInit

	/*
     * // Main //////////////////////////////////// public static void
	 * main(String[] args) throws Exception{ Frame f = new
	 * Frame("Test for SocksDialog"); f.add("Center", new
	 * Label("Fill the Dialog")); SocksDialog socksdialog = new SocksDialog(f);
	 * f.pack(); f.show(); f.addWindowListener(socksdialog); Proxy p =
	 * socksdialog.getProxy(); System.out.println("Selected: "+p); }
	 */

}// end class
