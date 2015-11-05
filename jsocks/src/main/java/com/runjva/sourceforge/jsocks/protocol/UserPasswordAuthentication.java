package com.runjva.sourceforge.jsocks.protocol;

/**
 * SOCKS5 User Password authentication scheme.
 */
public class UserPasswordAuthentication implements Authentication {

    /**
     * SOCKS ID for User/Password authentication method
     */
    public final static int METHOD_ID = 2;

    String userName, password;
    byte[] request;

    /**
     * Create an instance of UserPasswordAuthentication.
     *
     * @param userName User Name to send to SOCKS server.
     * @param password Password to send to SOCKS server.
     */
    public UserPasswordAuthentication(String userName, String password) {
        this.userName = userName;
        this.password = password;
        formRequest();
    }

    /**
     * Get the user name.
     *
     * @return User name.
     */
    public String getUser() {
        return userName;
    }

    /**
     * Get password
     *
     * @return Password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Does User/Password authentication as defined in rfc1929.
     *
     * @return An array containnig in, out streams, or null if authentication
     * fails.
     */
    public Object[] doSocksAuthentication(int methodId,
                                          java.net.Socket proxySocket) throws java.io.IOException {

        if (methodId != METHOD_ID) {
            return null;
        }

        final java.io.InputStream in = proxySocket.getInputStream();
        final java.io.OutputStream out = proxySocket.getOutputStream();

        out.write(request);
        final int version = in.read();
        if (version < 0) {
            return null; // Server closed connection
        }
        final int status = in.read();
        if (status != 0) {
            return null; // Server closed connection, or auth failed.
        }

        return new Object[]{in, out};
    }

    // Private methods
    // ////////////////

    /**
     * Convert UserName password in to binary form, ready to be send to server
     */
    private void formRequest() {
        final byte[] user_bytes = userName.getBytes();
        final byte[] password_bytes = password.getBytes();

        request = new byte[3 + user_bytes.length + password_bytes.length];
        request[0] = (byte) 1;
        request[1] = (byte) user_bytes.length;
        System.arraycopy(user_bytes, 0, request, 2, user_bytes.length);
        request[2 + user_bytes.length] = (byte) password_bytes.length;
        System.arraycopy(password_bytes, 0, request, 3 + user_bytes.length,
                password_bytes.length);
    }
}
