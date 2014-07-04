package io.bitsquare.util;

import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Validator
{
    private static final Logger log = LoggerFactory.getLogger(Validator.class);

    public static String validString(String value)
    {
        checkNotNull(value);
        checkArgument(value.length() > 0);
        return value;
    }

    public static long validNonNegativeLong(long value)
    {
        checkArgument(value >= 0);
        return value;
    }

    public static PeerAddress validPeerAddress(PeerAddress value)
    {
        checkNotNull(value);
        return value;
    }


}
