// Copyright 2005 Nick Mathewson, Roger Dingledine
// See LICENSE file for copying information
package net.freehaven.tor.control;

/**
 * Abstract interface whose methods are invoked when Tor sends us an event.
 *
 * @see TorControlConnection#setEventHandler
 * @see TorControlConnection#setEvents
 */
public interface EventHandler {
    /**
     * Invoked when a circuit's status has changed.
     * Possible values for <b>status</b> are:
     * <ul>
     * <li>"LAUNCHED" :  circuit ID assigned to new circuit</li>
     * <li>"BUILT"    :  all hops finished, can now accept streams</li>
     * <li>"EXTENDED" :  one more hop has been completed</li>
     * <li>"FAILED"   :  circuit closed (was not built)</li>
     * <li>"CLOSED"   :  circuit closed (was built)</li>
     * </ul>
     * <p/>
     * <b>circID</b> is the alphanumeric identifier of the affected circuit,
     * and <b>path</b> is a comma-separated list of alphanumeric ServerIDs.
     */
    public void circuitStatus(String status, String circID, String path);

    /**
     * Invoked when a stream's status has changed.
     * Possible values for <b>status</b> are:
     * <ul>
     * <li>"NEW"         :  New request to connect</li>
     * <li>"NEWRESOLVE"  :  New request to resolve an address</li>
     * <li>"SENTCONNECT" :  Sent a connect cell along a circuit</li>
     * <li>"SENTRESOLVE" :  Sent a resolve cell along a circuit</li>
     * <li>"SUCCEEDED"   :  Received a reply; stream established</li>
     * <li>"FAILED"      :  Stream failed and not retriable.</li>
     * <li>"CLOSED"      :  Stream closed</li>
     * <li>"DETACHED"    :  Detached from circuit; still retriable.</li>
     * </ul>
     * <p/>
     * <b>streamID</b> is the alphanumeric identifier of the affected stream,
     * and its <b>target</b> is specified as address:port.
     */
    public void streamStatus(String status, String streamID, String target);

    /**
     * Invoked when the status of a connection to an OR has changed.
     * Possible values for <b>status</b> are ["LAUNCHED" | "CONNECTED" | "FAILED" | "CLOSED"].
     * <b>orName</b> is the alphanumeric identifier of the OR affected.
     */
    public void orConnStatus(String status, String orName);

    /**
     * Invoked once per second. <b>read</b> and <b>written</b> are
     * the number of bytes read and written, respectively, in
     * the last second.
     */
    public void bandwidthUsed(long read, long written);

    /**
     * Invoked whenever Tor learns about new ORs.  The <b>orList</b> object
     * contains the alphanumeric ServerIDs associated with the new ORs.
     */
    public void newDescriptors(java.util.List<String> orList);

    /**
     * Invoked when Tor logs a message.
     * <b>severity</b> is one of ["DEBUG" | "INFO" | "NOTICE" | "WARN" | "ERR"],
     * and <b>msg</b> is the message string.
     */
    public void message(String severity, String msg);

    /**
     * Invoked when an unspecified message is received.
     * <type> is the message type, and <msg> is the message string.
     */
    public void unrecognized(String type, String msg);

}

