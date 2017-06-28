// Copyright 2005 Nick Mathewson, Roger Dingledine
// See LICENSE file for copying information
package net.freehaven.tor.control;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * A hashed digest of a secret password (used to set control connection
 * security.)
 * <p/>
 * For the actual hashing algorithm, see RFC2440's secret-to-key conversion.
 */
public class PasswordDigest {

    private final byte[] secret;
    private final String hashedKey;

    /**
     * Return a new password digest with a random secret and salt.
     */
    public static PasswordDigest generateDigest() {
        byte[] secret = new byte[20];
        SecureRandom rng = new SecureRandom();
        rng.nextBytes(secret);
        return new PasswordDigest(secret);
    }

    /**
     * Construct a new password digest with a given secret and random salt
     */
    public PasswordDigest(byte[] secret) {
        this(secret, null);
    }

    /**
     * Construct a new password digest with a given secret and random salt.
     * Note that the 9th byte of the specifier determines the number of hash
     * iterations as in RFC2440.
     */
    public PasswordDigest(byte[] secret, byte[] specifier) {
        this.secret = secret.clone();
        if (specifier == null) {
            specifier = new byte[9];
            SecureRandom rng = new SecureRandom();
            rng.nextBytes(specifier);
            specifier[8] = 96;
        }
        hashedKey = "16:" + encodeBytes(secretToKey(secret, specifier));
    }

    /**
     * Return the secret used to generate this password hash.
     */
    public byte[] getSecret() {
        return secret.clone();
    }

    /**
     * Return the hashed password in the format used by Tor.
     */
    public String getHashedPassword() {
        return hashedKey;
    }

    /**
     * Parameter used by RFC2440's s2k algorithm.
     */
    private static final int EXPBIAS = 6;

    /**
     * Implement rfc2440 s2k
     */
    public static byte[] secretToKey(byte[] secret, byte[] specifier) {
        MessageDigest d;
        try {
            d = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Can't run without sha-1.");
        }
        int c = (specifier[8]) & 0xff;
        int count = (16 + (c & 15)) << ((c >> 4) + EXPBIAS);

        byte[] tmp = new byte[8 + secret.length];
        System.arraycopy(specifier, 0, tmp, 0, 8);
        System.arraycopy(secret, 0, tmp, 8, secret.length);
        while (count > 0) {
            if (count >= tmp.length) {
                d.update(tmp);
                count -= tmp.length;
            } else {
                d.update(tmp, 0, count);
                count = 0;
            }
        }
        byte[] key = new byte[20 + 9];
        System.arraycopy(d.digest(), 0, key, 9, 20);
        System.arraycopy(specifier, 0, key, 0, 9);
        return key;
    }

    /**
     * Return a hexadecimal encoding of a byte array.
     */
    // XXX There must be a better way to do this in Java.
    private static final String encodeBytes(byte[] ba) {
        return Bytes.hex(ba);
    }

}

