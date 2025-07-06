package bisq.core.support.dispute.agent;

import bisq.core.locale.Res;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DisputeAgentLookupMapTest {

    @Test
    void testGetMatrixUserName_refundagent3() {
        assertEquals(
            "refundagent3",
            DisputeAgentLookupMap.getMatrixUserName("yjlcxr6rho6zkpecwdp3vlpduzcl7i6cbgaquvxqmvsbw3dnheus6qad.onion:9999")
        );
    }

    @Test
    void testGetMatrixUserName_existingMediators() {
        // Test another existing mediator mapping
        assertEquals(
            "luis3672",
            DisputeAgentLookupMap.getMatrixUserName("aguejpkhhl67nbtifvekfjvlcyagudi6d2apalcwxw7fl5n7qm2ll5id.onion:9999")
        );
    }

    @Test
    void testGetMatrixUserName_unknownAddress() {
        // Test behavior with an unknown onion address
        // The code returns a special "Not Available" string, so we test for that.
        assertEquals(
            Res.get("shared.na"),
            DisputeAgentLookupMap.getMatrixUserName("some-fake-address-that-does-not-exist.onion:9999")
        );
    }
}
