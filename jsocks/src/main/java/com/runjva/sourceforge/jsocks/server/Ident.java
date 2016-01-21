package com.runjva.sourceforge.jsocks.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.StringTokenizer;

/**
 * Class Ident provides means to obtain user name of the owner of the socket on
 * remote machine, providing remote machine runs identd daemon.
 * <p>
 * To use it: <tt><pre>
 * Socket s = ss.accept();
 * Ident id = new Ident(s);
 * if(id.successful) goUseUser(id.userName);
 * else handleIdentError(id.errorCode,id.errorMessage)
 * </pre></tt>
 */
public class Ident {

    Logger log = LoggerFactory.getLogger(Ident.class);

    /**
     * Error Message can be null.
     */
    public String errorMessage;

    /**
     * Host type as returned by daemon, can be null, if error happened
     */
    public String hostType;

    /**
     * User name as returned by the identd daemon, or null, if it failed
     */
    public String userName;

    /**
     * If this is true then userName and hostType contain valid values. Else
     * errorCode contain the error code, and errorMessage contains the
     * corresponding message.
     */
    public boolean successful;

    /**
     * Error code
     */
    public int errorCode;

    /**
     * Identd on port 113 can't be contacted
     */
    public static final int ERR_NO_CONNECT = 1;

    /**
     * Connection timed out
     */
    public static final int ERR_TIMEOUT = 2;

    /**
     * Identd daemon responded with ERROR, in this case errorMessage contains
     * the string explanation, as send by the daemon.
     */
    public static final int ERR_PROTOCOL = 3;

    /**
     * When parsing server response protocol error happened.
     */
    public static final int ERR_PROTOCOL_INCORRECT = 4;

    /**
     * Maximum amount of time we should wait before dropping the connection to
     * identd server.Setting it to 0 implies infinit timeout.
     */
    public static final int connectionTimeout = 10000;

    /**
     * Constructor tries to connect to Identd daemon on the host of the given
     * socket, and retrieve user name of the owner of given socket connection on
     * remote machine. After constructor returns public fields are initialised
     * to whatever the server returned.
     * <p>
     * If user name was successfully retrieved successful is set to true, and
     * userName and hostType are set to whatever server returned. If however for
     * some reason user name was not obtained, successful is set to false and
     * errorCode contains the code explaining the reason of failure, and
     * errorMessage contains human readable explanation.
     * <p>
     * Constructor may block, for a while.
     *
     * @param s Socket whose ownership on remote end should be obtained.
     */
    public Ident(Socket s) {
        Socket sock = null;
        successful = false; // We are pessimistic

        try {
            sock = new Socket(s.getInetAddress(), 113);
            sock.setSoTimeout(connectionTimeout);
            final byte[] request = ("" + s.getPort() + " , " + s.getLocalPort() + "\r\n")
                    .getBytes();

            sock.getOutputStream().write(request);

            final BufferedReader in = new BufferedReader(new InputStreamReader(
                    sock.getInputStream()));

            parseResponse(in.readLine());

        } catch (final InterruptedIOException iioe) {
            errorCode = ERR_TIMEOUT;
            errorMessage = "Connection to identd timed out.";
        } catch (final ConnectException ce) {
            errorCode = ERR_NO_CONNECT;
            errorMessage = "Connection to identd server failed.";

        } catch (final IOException ioe) {
            errorCode = ERR_NO_CONNECT;
            errorMessage = "" + ioe;
        } finally {
            try {
                if (sock != null) {
                    sock.close();
                }
            } catch (final IOException ioe) {
                log.warn("Could not close socket", ioe);
            }
        }
    }

    private void parseResponse(String response) {
        if (response == null) {
            errorCode = ERR_PROTOCOL_INCORRECT;
            errorMessage = "Identd server closed connection.";
            return;
        }

        final StringTokenizer st = new StringTokenizer(response, ":");
        if (st.countTokens() < 3) {
            errorCode = ERR_PROTOCOL_INCORRECT;
            errorMessage = "Can't parse server response.";
            return;
        }

        st.nextToken(); // Discard first token, it's basically what we have send
        final String command = st.nextToken().trim().toUpperCase();

        if (command.equals("USERID") && (st.countTokens() >= 2)) {
            successful = true;
            hostType = st.nextToken().trim();
            userName = st.nextToken("").substring(1);// Get all that is left
        } else if (command.equals("ERROR")) {
            errorCode = ERR_PROTOCOL;
            errorMessage = st.nextToken();
        } else {
            errorCode = ERR_PROTOCOL_INCORRECT;
            System.out.println("Opa!");
            errorMessage = "Can't parse server response.";
        }

    }

    // /////////////////////////////////////////////
    // USED for Testing
    /*
     * public static void main(String[] args) throws IOException{
	 * 
	 * Socket s = null; s = new Socket("gp101-16", 1391);
	 * 
	 * Ident id = new Ident(s); if(id.successful){
	 * System.out.println("User: "+id.userName);
	 * System.out.println("HostType: "+id.hostType); }else{
	 * System.out.println("ErrorCode: "+id.errorCode);
	 * System.out.println("ErrorMessage: "+id.errorMessage);
	 * 
	 * }
	 * 
	 * if(s!= null) s.close(); } //
	 */

}
