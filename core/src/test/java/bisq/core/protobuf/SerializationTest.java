package bisq.core.protobuf;

import bisq.common.util.Hex;

import protobuf.StringMapEntry;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SerializationTest {
    @Test
    public void oldInventoryBytes() {
        // we use GetInventoryResponse as our test case (a wrapper that uses map<string, string> in old protobuf definition)
        // we assert that old extra_data style key value string maps are serialized the same way in new protobuf definition
        // we need to use one entry because we cannot determine order of map when it's more than 1 entry
        var oldInventoryHex = Hex.encode(
                oldprotobuf.GetInventoryResponse.newBuilder().putInventory("foo", "bar").build().toByteArray()
        );
        var newInventoryHex = Hex.encode(
                protobuf.GetInventoryResponse.newBuilder().addInventory(StringMapEntry.newBuilder().setKey("foo").setValue("bar").build()).build().toByteArray()
        );

        assertEquals(oldInventoryHex, newInventoryHex);

        var emptyOldInventoryHex = Hex.encode(
                oldprotobuf.GetInventoryResponse.newBuilder().putInventory("foo", "").build().toByteArray()
        );
        var emptyNewInventoryHex = Hex.encode(
                protobuf.GetInventoryResponse.newBuilder().addInventory(StringMapEntry.newBuilder().setKey("foo").setValue("").build()).build().toByteArray()
        );

        assertEquals(emptyOldInventoryHex, emptyNewInventoryHex);

        // but we can also test if the same bytes can be deserialized with more than 1 entry using the new map

        var threeEntriesNewInv = protobuf.GetInventoryResponse.newBuilder()
                .addInventory(StringMapEntry.newBuilder().setKey("foo").setValue("bar").build())
                .addInventory(StringMapEntry.newBuilder().setKey("goo").setValue("buz").build())
                .addInventory(StringMapEntry.newBuilder().setKey("bud").setValue("tud").build())
                .build()
                .toByteArray();

        try {
            var threeEntriesOldInv = oldprotobuf.GetInventoryResponse.parseFrom(threeEntriesNewInv);
            assertEquals(3, threeEntriesOldInv.getInventoryCount());
            assertEquals("bar", threeEntriesOldInv.getInventoryMap().get("foo"));
            assertEquals("buz", threeEntriesOldInv.getInventoryMap().get("goo"));
            assertEquals("tud", threeEntriesOldInv.getInventoryMap().get("bud"));
        } catch (Exception ignored) {
            fail("serialized bytes using new protobuf definition was not parsable by old protobuf definitions");
        }
    }

    @Test
    public void daoStateBytes() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        byte[] trimmedDumpData = new byte[] {};
        try (InputStream inputStream = classLoader.getResourceAsStream("serialization_test_data/DaoStateDump.bin")) {
            assertNotNull(inputStream, "Binary file should be found in resources");
            trimmedDumpData = inputStream.readAllBytes();
        } catch (Exception e) {
            fail("Failed to read dumped data file");
        }
        assertTrue(trimmedDumpData.length > 0);


        try {
            var oldParsed = oldprotobuf.DaoState.parseFrom(trimmedDumpData);
            var newParsed = protobuf.DaoState.parseFrom(trimmedDumpData);
            assertEquals(oldParsed.getSerializedSize(), newParsed.getSerializedSize());
            // ensure that all modified data exists in edited definition
            for (var oldEntry : oldParsed.getUnspentTxOutputMapMap().entrySet()) {
                assertTrue(
                        newParsed.getUnspentTxOutputMapEntriesList().contains(
                                protobuf.BaseTxOutputMapEntry.newBuilder()
                                        .setKey(oldEntry.getKey())
                                        .setValue(protobuf.BaseTxOutput.parseFrom(oldEntry.getValue().toByteArray()))
                                        .build()
                        )
                );
            }
            for (var oldEntry : oldParsed.getIssuanceMapMap().entrySet()) {
                assertTrue(
                        newParsed.getIssuanceMapEntriesList().contains(
                                protobuf.IssuanceMapEntry.newBuilder()
                                        .setKey(oldEntry.getKey())
                                        .setValue(protobuf.Issuance.parseFrom(oldEntry.getValue().toByteArray()))
                                        .build()
                        )
                );
            }
            for (var oldEntry : oldParsed.getSpentInfoMapMap().entrySet()) {
                assertTrue(
                        newParsed.getSpentInfoMapEntriesList().contains(
                                protobuf.SpentInfoMapEntry.newBuilder()
                                        .setKey(oldEntry.getKey())
                                        .setValue(protobuf.SpentInfo.parseFrom(oldEntry.getValue().toByteArray()))
                                        .build()
                        )
                );
            }
        } catch (com.google.protobuf.InvalidProtocolBufferException ignored) {
            fail("Failed to parse into both new and old protobuf classes from the same bytes");
        }
    }
}
