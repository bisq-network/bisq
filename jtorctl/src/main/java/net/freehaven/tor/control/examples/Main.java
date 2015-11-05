// Copyright 2005 Nick Mathewson, Roger Dingledine
// See LICENSE file for copying information
package net.freehaven.tor.control.examples;

import net.freehaven.tor.control.*;

import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

public class Main implements TorControlCommands {

    public static void main(String args[]) {
        if (args.length < 1) {
            System.err.println("No command given.");
            return;
        }
        try {
            if (args[0].equals("set-config")) {
                setConfig(args);
            } else if (args[0].equals("get-config")) {
                getConfig(args);
            } else if (args[0].equals("get-info")) {
                getInfo(args);
            } else if (args[0].equals("listen")) {
                listenForEvents(args);
            } else if (args[0].equals("signal")) {
                signal(args);
            } else if (args[0].equals("auth")) {
                authDemo(args);
            } else {
                System.err.println("Unrecognized command: " + args[0]);
            }
        } catch (EOFException ex) {
            System.out.println("Control socket closed by Tor.");
        } catch (TorControlError ex) {
            System.err.println("Error from Tor process: " +
                    ex + " [" + ex.getErrorMsg() + "]");
        } catch (IOException ex) {
            System.err.println("IO exception when talking to Tor process: " +
                    ex);
            ex.printStackTrace(System.err);
        }
    }

    private static TorControlConnection getConnection(String[] args,
                                                      boolean daemon) throws IOException {
        Socket s = new Socket("127.0.0.1", 9100);
        TorControlConnection conn = new TorControlConnection(s);
        conn.launchThread(daemon);
        conn.authenticate(new byte[0]);
        return conn;
    }

    private static TorControlConnection getConnection(String[] args)
            throws IOException {
        return getConnection(args, true);
    }

    public static void setConfig(String[] args) throws IOException {
        // Usage: "set-config [-save] key value key value key value"
        TorControlConnection conn = getConnection(args);
        ArrayList<String> lst = new ArrayList<String>();
        int i = 1;
        boolean save = false;
        if (args[i].equals("-save")) {
            save = true;
            ++i;
        }
        for (; i < args.length; i += 2) {
            lst.add(args[i] + " " + args[i + 1]);
        }
        conn.setConf(lst);
        if (save) {
            conn.saveConf();
        }
    }

    public static void getConfig(String[] args) throws IOException {
        // Usage: get-config key key key
        TorControlConnection conn = getConnection(args);
        List<ConfigEntry> lst = conn.getConf(Arrays.asList(args).subList(1, args.length));
        for (Iterator<ConfigEntry> i = lst.iterator(); i.hasNext(); ) {
            ConfigEntry e = i.next();
            System.out.println("KEY: " + e.key);
            System.out.println("VAL: " + e.value);
        }
    }

    public static void getInfo(String[] args) throws IOException {
        TorControlConnection conn = getConnection(args);
        Map<String, String> m = conn.getInfo(Arrays.asList(args).subList(1, args.length));
        for (Iterator<Map.Entry<String, String>> i = m.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry<String, String> e = i.next();
            System.out.println("KEY: " + e.getKey());
            System.out.println("VAL: " + e.getValue());
        }
    }

    public static void listenForEvents(String[] args) throws IOException {
        // Usage: listen [circ|stream|orconn|bw|newdesc|info|notice|warn|error]*
        TorControlConnection conn = getConnection(args, false);
        ArrayList<String> lst = new ArrayList<String>();
        for (int i = 1; i < args.length; ++i) {
            lst.add(args[i]);
        }
        conn.setEventHandler(
                new DebuggingEventHandler(new PrintWriter(System.out, true)));
        conn.setEvents(lst);
    }

    public static void signal(String[] args) throws IOException {
        // Usage signal [reload|shutdown|dump|debug|halt]
        TorControlConnection conn = getConnection(args, false);
        // distinguish shutdown signal from other signals
        if ("SHUTDOWN".equalsIgnoreCase(args[1])
                || "HALT".equalsIgnoreCase(args[1])) {
            conn.shutdownTor(args[1].toUpperCase());
        } else {
            conn.signal(args[1].toUpperCase());
        }
    }

    public static void authDemo(String[] args) throws IOException {

        PasswordDigest pwd = PasswordDigest.generateDigest();
        Socket s = new Socket("127.0.0.1", 9100);
        TorControlConnection conn = new TorControlConnection(s);
        conn.launchThread(true);
        conn.authenticate(new byte[0]);

        conn.setConf("HashedControlPassword", pwd.getHashedPassword());

        s = new Socket("127.0.0.1", 9100);
        conn = new TorControlConnection(s);
        conn.launchThread(true);
        conn.authenticate(pwd.getSecret());
    }

}

