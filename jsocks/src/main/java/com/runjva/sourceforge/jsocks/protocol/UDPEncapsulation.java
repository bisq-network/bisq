package com.runjva.sourceforge.jsocks.protocol;

/**
 * This interface provides for datagram encapsulation for SOCKSv5 protocol.
 * <p>
 * SOCKSv5 allows for datagrams to be encapsulated for purposes of integrity
 * and/or authenticity. How it should be done is aggreed during the
 * authentication stage, and is authentication dependent. This interface is
 * provided to allow this encapsulation.
 *
 * @see Authentication
 */
public interface UDPEncapsulation {

    /**
     * This method should provide any authentication depended transformation on
     * datagrams being send from/to the client.
     *
     * @param data Datagram data (including any SOCKS related bytes), to be
     *             encapsulated/decapsulated.
     * @param out  Wether the data is being send out. If true method should
     *             encapsulate/encrypt data, otherwise it should decapsulate/
     *             decrypt data.
     * @return Should return byte array containing data after transformation. It
     * is possible to return same array as input, if transformation only
     * involves bit mangling, and no additional data is being added or
     * removed.
     * @throw IOException if for some reason data can be transformed correctly.
     */
    byte[] udpEncapsulate(byte[] data, boolean out) throws java.io.IOException;
}
