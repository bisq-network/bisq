/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.storage;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.mocks.DateTolerantPayloadStub;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static bisq.network.p2p.storage.TestState.SavedTestState;
import static java.util.stream.Stream.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests of the P2PDataStore entry points that use the PersistableNetworkPayload type
 *
 * The abstract base class AddPersistableNetworkPayloadTest defines the common test cases and Payload type
 * that needs to be tested is set up through extending the base class and overriding the createInstance() methods to
 * give the common tests a different payload to test.
 *
 * Each subclass (Payload type) can optionally add additional tests that verify functionality only relevant
 * to that payload.
 *
 * Each test case is run through 3 entry points to verify the correct behavior:
 *
 * 1 & 2 Client API [addPersistableNetworkPayload(reBroadcast=(true && false))]
 * 3.    onMessage() [onMessage(AddPersistableNetworkPayloadMessage)]
 */
@SuppressWarnings("unused")
public class P2PDataStoragePersistableNetworkPayloadTest {

    public abstract static class AddPersistableNetworkPayloadTest {
        TestState testState;

        PersistableNetworkPayload persistableNetworkPayload;

        abstract PersistableNetworkPayload createInstance();

        enum TestCase {
            PUBLIC_API,
            ON_MESSAGE,
        }

        void assertAndDoAdd(PersistableNetworkPayload persistableNetworkPayload,
                            TestCase testCase,
                            boolean reBroadcast,
                            boolean expectedReturnValue,
                            boolean expectedHashMapAndDataStoreUpdated,
                            boolean expectedListenersSignaled,
                            boolean expectedBroadcast) {
            SavedTestState beforeState = this.testState.saveTestState(persistableNetworkPayload);

            if (testCase == TestCase.PUBLIC_API) {
                assertEquals(expectedReturnValue,
                        this.testState.mockedStorage.addPersistableNetworkPayload(persistableNetworkPayload, TestState.getTestNodeAddress(), reBroadcast));
            } else { // onMessage
                Connection mockedConnection = mock(Connection.class);
                when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

                testState.mockedStorage.onMessage(new AddPersistableNetworkPayloadMessage(persistableNetworkPayload), mockedConnection);
            }

            this.testState.verifyPersistableAdd(beforeState, persistableNetworkPayload, expectedHashMapAndDataStoreUpdated, expectedListenersSignaled, expectedBroadcast);
        }

        @BeforeEach
        public void setup() {
            this.persistableNetworkPayload = this.createInstance();

            this.testState = new TestState();
        }

        static Stream<Object[]> data() {
            return of(
                    new Object[]{TestCase.ON_MESSAGE, false},
                    new Object[]{TestCase.PUBLIC_API, true},
                    new Object[]{TestCase.PUBLIC_API, false}
            );
        }

        @MethodSource("data")
        @ParameterizedTest(name = "{index}: Test with TestCase={0} allowBroadcast={1} reBroadcast={2} checkDate={3}")
        public void addPersistableNetworkPayload(TestCase testCase, boolean reBroadcast) {
            // First add should succeed regardless of parameters
            assertAndDoAdd(this.persistableNetworkPayload, testCase, reBroadcast, true, true, true, true);
        }

        @MethodSource("data")
        @ParameterizedTest(name = "{index}: Test with TestCase={0} allowBroadcast={1} reBroadcast={2} checkDate={3}")
        public void addPersistableNetworkPayloadDuplicate(TestCase testCase, boolean reBroadcast) {
            assertAndDoAdd(this.persistableNetworkPayload, testCase, reBroadcast, true, true, true, true);

            // We return true and broadcast if reBroadcast is set
            // assertAndDoAdd(this.persistableNetworkPayload, testCase, reBroadcast, reBroadcast, false, false, reBroadcast);
        }
    }

    /**
     * Runs the common test cases defined in AddPersistableNetworkPayloadTest against a PersistableNetworkPayload
     */
    public static class AddPersistableNetworkPayloadStubTest extends AddPersistableNetworkPayloadTest {
        @Override
        PersistableNetworkPayloadStub createInstance() {
            return new PersistableNetworkPayloadStub(true);
        }

        @MethodSource("data")
        @ParameterizedTest(name = "{index}: Test with TestCase={0} allowBroadcast={1} reBroadcast={2} checkDate={3}")
        public void invalidHash(TestCase testCase, boolean reBroadcast) {
            PersistableNetworkPayload persistableNetworkPayload = new PersistableNetworkPayloadStub(false);

            assertAndDoAdd(persistableNetworkPayload, testCase, reBroadcast, false, false, false, false);
        }
    }

    /**
     * Runs the common test cases defined in AddPersistableNetworkPayloadTest against a PersistableNetworkPayload using
     * the DateTolerant marker interface.
     */
    public static class AddPersistableDateTolerantPayloadTest extends AddPersistableNetworkPayloadTest {

        @Override
        DateTolerantPayloadStub createInstance() {
            return new DateTolerantPayloadStub(true);

        }

        @MethodSource("data")
        @ParameterizedTest(name = "{index}: Test with TestCase={0} allowBroadcast={1} reBroadcast={2} checkDate={3}")
        public void outOfTolerance(TestCase testCase, boolean reBroadcast) {
            PersistableNetworkPayload persistableNetworkPayload = new DateTolerantPayloadStub(false);

            // The onMessage path checks for tolerance
            boolean expectedReturn = testCase != TestCase.ON_MESSAGE;

            assertAndDoAdd(persistableNetworkPayload, testCase, reBroadcast, expectedReturn, expectedReturn, expectedReturn, expectedReturn);
        }
    }
}
