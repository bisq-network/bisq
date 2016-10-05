package io.bitsquare.common.crypto;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by mike on 09/08/16.
 */
public class HashTest {
    @Test
    /** test for effects due to caching of message digest */
    public void getHash() throws Exception {
        byte[] a = RandomUtils.nextBytes(10);
        byte[] b = RandomUtils.nextBytes(10);
        byte[] c = RandomUtils.nextBytes(10);

        byte[] ahash = Hash.getHash(a);
        byte[] bhash = Hash.getHash(b);
        byte[] chash = Hash.getHash(c);
        assertTrue(Arrays.equals(ahash, Hash.getHash(a)));
        assertTrue(Arrays.equals(bhash, Hash.getHash(b)));
        assertTrue(Arrays.equals(chash, Hash.getHash(c)));

        // test tampering
        assertEquals("6d657373616765", Hash.getHashAsHex("message"));
    }
}
