package bisq;

import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;



import protobuf.TempProposalPayload;

public class HashMapOrderTest {
    @Test
    public void mapIsNotOrderPreservingButAlwaysEnforcesKeyOrder() {
        TempProposalPayload prop = TempProposalPayload.newBuilder().putAllExtraData(Map.of("k1", "v1", "k2", "v2")).build();
        byte[] bytes1 = prop.toByteArray();
        TempProposalPayload prop2 = TempProposalPayload.newBuilder().putAllExtraData(Map.of("k2", "v2", "k1", "v1")).build();
        byte[] bytes2 = prop2.toByteArray();
        assertArrayEquals(bytes1, bytes2);
    }
}
