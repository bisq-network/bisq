package bisq;

import java.util.TreeMap;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;



import protobuf.TempProposalPayload;

public class HashMapOrderTest {
    @Test
    public void mapIsNotOrderPreservingButAlwaysEnforcesKeyOrder() {
        TreeMap<String, String> order1 = new TreeMap<>();
        order1.put("k1", "v1");
        order1.put("k2", "v2");
        TreeMap<String, String> order2 = new TreeMap<>();
        order2.put("k2", "v2");
        order2.put("k1", "v1");
        TempProposalPayload prop = TempProposalPayload.newBuilder().putAllExtraData(order1).build();
        byte[] bytes1 = prop.toByteArray();
        TempProposalPayload prop2 = TempProposalPayload.newBuilder().putAllExtraData(order2).build();
        byte[] bytes2 = prop2.toByteArray();
        assertArrayEquals(bytes1, bytes2);
    }
}
