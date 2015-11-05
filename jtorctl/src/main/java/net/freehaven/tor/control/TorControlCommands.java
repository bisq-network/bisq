// Copyright 2005 Nick Mathewson, Roger Dingledine
// See LICENSE file for copying information
package net.freehaven.tor.control;

/**
 * Interface defining constants used by the Tor controller protocol.
 */
// XXXX Take documentation for these from control-spec.txt
public interface TorControlCommands {

    public static final short CMD_ERROR = 0x0000;
    public static final short CMD_DONE = 0x0001;
    public static final short CMD_SETCONF = 0x0002;
    public static final short CMD_GETCONF = 0x0003;
    public static final short CMD_CONFVALUE = 0x0004;
    public static final short CMD_SETEVENTS = 0x0005;
    public static final short CMD_EVENT = 0x0006;
    public static final short CMD_AUTH = 0x0007;
    public static final short CMD_SAVECONF = 0x0008;
    public static final short CMD_SIGNAL = 0x0009;
    public static final short CMD_MAPADDRESS = 0x000A;
    public static final short CMD_GETINFO = 0x000B;
    public static final short CMD_INFOVALUE = 0x000C;
    public static final short CMD_EXTENDCIRCUIT = 0x000D;
    public static final short CMD_ATTACHSTREAM = 0x000E;
    public static final short CMD_POSTDESCRIPTOR = 0x000F;
    public static final short CMD_FRAGMENTHEADER = 0x0010;
    public static final short CMD_FRAGMENT = 0x0011;
    public static final short CMD_REDIRECTSTREAM = 0x0012;
    public static final short CMD_CLOSESTREAM = 0x0013;
    public static final short CMD_CLOSECIRCUIT = 0x0014;

    public static final String[] CMD_NAMES = {
            "ERROR",
            "DONE",
            "SETCONF",
            "GETCONF",
            "CONFVALUE",
            "SETEVENTS",
            "EVENT",
            "AUTH",
            "SAVECONF",
            "SIGNAL",
            "MAPADDRESS",
            "GETINFO",
            "INFOVALUE",
            "EXTENDCIRCUIT",
            "ATTACHSTREAM",
            "POSTDESCRIPTOR",
            "FRAGMENTHEADER",
            "FRAGMENT",
            "REDIRECTSTREAM",
            "CLOSESTREAM",
            "CLOSECIRCUIT",
    };

    public static final short EVENT_CIRCSTATUS = 0x0001;
    public static final short EVENT_STREAMSTATUS = 0x0002;
    public static final short EVENT_ORCONNSTATUS = 0x0003;
    public static final short EVENT_BANDWIDTH = 0x0004;
    public static final short EVENT_NEWDESCRIPTOR = 0x0006;
    public static final short EVENT_MSG_DEBUG = 0x0007;
    public static final short EVENT_MSG_INFO = 0x0008;
    public static final short EVENT_MSG_NOTICE = 0x0009;
    public static final short EVENT_MSG_WARN = 0x000A;
    public static final short EVENT_MSG_ERROR = 0x000B;

    public static final String[] EVENT_NAMES = {
            "(0)",
            "CIRC",
            "STREAM",
            "ORCONN",
            "BW",
            "OLDLOG",
            "NEWDESC",
            "DEBUG",
            "INFO",
            "NOTICE",
            "WARN",
            "ERR",
    };

    public static final byte CIRC_STATUS_LAUNCHED = 0x01;
    public static final byte CIRC_STATUS_BUILT = 0x02;
    public static final byte CIRC_STATUS_EXTENDED = 0x03;
    public static final byte CIRC_STATUS_FAILED = 0x04;
    public static final byte CIRC_STATUS_CLOSED = 0x05;

    public static final String[] CIRC_STATUS_NAMES = {
            "LAUNCHED",
            "BUILT",
            "EXTENDED",
            "FAILED",
            "CLOSED",
    };

    public static final byte STREAM_STATUS_SENT_CONNECT = 0x00;
    public static final byte STREAM_STATUS_SENT_RESOLVE = 0x01;
    public static final byte STREAM_STATUS_SUCCEEDED = 0x02;
    public static final byte STREAM_STATUS_FAILED = 0x03;
    public static final byte STREAM_STATUS_CLOSED = 0x04;
    public static final byte STREAM_STATUS_NEW_CONNECT = 0x05;
    public static final byte STREAM_STATUS_NEW_RESOLVE = 0x06;
    public static final byte STREAM_STATUS_DETACHED = 0x07;

    public static final String[] STREAM_STATUS_NAMES = {
            "SENT_CONNECT",
            "SENT_RESOLVE",
            "SUCCEEDED",
            "FAILED",
            "CLOSED",
            "NEW_CONNECT",
            "NEW_RESOLVE",
            "DETACHED"
    };

    public static final byte OR_CONN_STATUS_LAUNCHED = 0x00;
    public static final byte OR_CONN_STATUS_CONNECTED = 0x01;
    public static final byte OR_CONN_STATUS_FAILED = 0x02;
    public static final byte OR_CONN_STATUS_CLOSED = 0x03;

    public static final String[] OR_CONN_STATUS_NAMES = {
            "LAUNCHED", "CONNECTED", "FAILED", "CLOSED"
    };

    public static final byte SIGNAL_HUP = 0x01;
    public static final byte SIGNAL_INT = 0x02;
    public static final byte SIGNAL_USR1 = 0x0A;
    public static final byte SIGNAL_USR2 = 0x0C;
    public static final byte SIGNAL_TERM = 0x0F;

    public static final String ERROR_MSGS[] = {
            "Unspecified error",
            "Internal error",
            "Unrecognized message type",
            "Syntax error",
            "Unrecognized configuration key",
            "Invalid configuration value",
            "Unrecognized byte code",
            "Unauthorized",
            "Failed authentication attempt",
            "Resource exhausted",
            "No such stream",
            "No such circuit",
            "No such OR",
    };

}

