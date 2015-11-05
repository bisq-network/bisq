// Copyright 2005 Nick Mathewson, Roger Dingledine
// See LICENSE file for copying information
package net.freehaven.tor.control.examples;

import net.freehaven.tor.control.EventHandler;

import java.io.PrintWriter;
import java.util.Iterator;

public class DebuggingEventHandler implements EventHandler {

    private final PrintWriter out;

    public DebuggingEventHandler(PrintWriter p) {
        out = p;
    }

    public void circuitStatus(String status, String circID, String path) {
        out.println("Circuit " + circID + " is now " + status + " (path=" + path + ")");
    }

    public void streamStatus(String status, String streamID, String target) {
        out.println("Stream " + streamID + " is now " + status + " (target=" + target + ")");
    }

    public void orConnStatus(String status, String orName) {
        out.println("OR connection to " + orName + " is now " + status);
    }

    public void bandwidthUsed(long read, long written) {
        out.println("Bandwidth usage: " + read + " bytes read; " +
                written + " bytes written.");
    }

    public void newDescriptors(java.util.List<String> orList) {
        out.println("New descriptors for routers:");
        for (Iterator<String> i = orList.iterator(); i.hasNext(); )
            out.println("   " + i.next());
    }

    public void message(String type, String msg) {
        out.println("[" + type + "] " + msg.trim());
    }

    public void unrecognized(String type, String msg) {
        out.println("unrecognized event [" + type + "] " + msg.trim());
    }

}

