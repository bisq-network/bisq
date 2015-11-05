package com.runjva.sourceforge.jsocks.server;

/**
 * Interface which provides for user validation, based on user name password and
 * where it connects from.
 */
public interface UserValidation {
    /**
     * Implementations of this interface are expected to use some or all of the
     * information provided plus any information they can extract from other
     * sources to decide wether given user should be allowed access to SOCKS
     * server, or whatever you use it for.
     *
     * @param username   User whom implementation should validate.
     * @param password   Password this user provided.
     * @param connection Socket which user used to connect to the server.
     * @return true to indicate user is valid, false otherwise.
     */
    boolean isUserValid(String username, String password,
                        java.net.Socket connection);
}
