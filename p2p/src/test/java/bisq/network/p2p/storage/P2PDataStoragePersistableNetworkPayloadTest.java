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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static bisq.network.p2p.storage.TestState.*;

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
 * Each test case is run through 4 entry points to verify the correct behavior:
 *
 * 1.    RequestData path [addPersistableNetworkPayloadFromInitialRequest]
 * 2 & 3 Client API [addPersistableNetworkPayload(reBroadcast=(true && false))]
 * 4.    onMessage() [onMessage(AddPersistableNetworkPayloadMessage)]
 */
public class P2PDataStoragePersistableNetworkPayloadTest {

    @RunWith(Parameterized.class)
    public abstract static class AddPersistableNetworkPayloadTest {
        TestState testState;

        @Parameterized.Parameter(0)
        public TestCase testCase;

        @Parameterized.Parameter(1)
        public boolean allowBroadcast;

        @Parameterized.Parameter(2)
        public boolean reBroadcast;

        @Parameterized.Parameter(3)
        public boolean checkDate;

        PersistableNetworkPayload persistableNetworkPayload;

        abstract PersistableNetworkPayload createInstance();

        enum TestCase {
            PUBLIC_API,
            ON_MESSAGE,
            INIT,
        }

        boolean expectBroadcastOnStateChange() {
            return this.testCase != TestCase.INIT;
        }

        boolean expectedIsDataOwner() {
            return this.testCase == TestCase.PUBLIC_API;
        }

        void doAddAndVerify(PersistableNetworkPayload persistableNetworkPayload, boolean expectedReturnValue, boolean expectedStateChange) {
            SavedTestState beforeState = this.testState.saveTestState(persistableNetworkPayload);

            if (this.testCase == TestCase.INIT) {
                Assert.assertEquals(expectedReturnValue, this.testState.mockedStorage.addPersistableNetworkPayloadFromInitialRequest(persistableNetworkPayload));
            } else if (this.testCase == TestCase.PUBLIC_API) {
                Assert.assertEquals(expectedReturnValue,
                        this.testState.mockedStorage.addPersistableNetworkPayload(persistableNetworkPayload, TestState.getTestNodeAddress(), true, this.allowBroadcast, this.reBroadcast, this.checkDate));
            } else { // onMessage
                Connection mockedConnection = mock(Connection.class);
                when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

                testState.mockedStorage.onMessage(new AddPersistableNetworkPayloadMessage(persistableNetworkPayload), mockedConnection);
            }

            this.testState.verifyPersistableAdd(beforeState, persistableNetworkPayload, expectedStateChange, this.expectBroadcastOnStateChange(), this.expectedIsDataOwner());
        }

        @Before
        public void setup() {
            this.persistableNetworkPayload = this.createInstance();

            this.testState = new TestState();
        }

        @Parameterized.Parameters(name = "{index}: Test with TestCase={0} allowBroadcast={1} reBroadcast={2} checkDate={3}")
        public static Collection<Object[]> data() {
            List<Object[]> data = new ArrayList<>();

            // Init doesn't use other parameters
            data.add(new Object[] { TestCase.INIT, false, false, false });

            // onMessage doesn't use other parameters
            data.add(new Object[] { TestCase.ON_MESSAGE, false, false, false });

            // Client API uses two permutations
            // Normal path
            data.add(new Object[] { TestCase.PUBLIC_API, true, true, false });

            // Refresh path
            data.add(new Object[] { TestCase.PUBLIC_API, true, false, false });

            return data;
        }

        @Test
        public void addPersistableNetworkPayload() {
            // First add should succeed regardless of parameters
            doAddAndVerify(this.persistableNetworkPayload, true, true);
        }

        @Test
        public void addPersistableNetworkPayloadDuplicate() {
            doAddAndVerify(this.persistableNetworkPayload, true, true);

            // Second call only succeeds if reBroadcast was set or we are adding through the init
            // path which just overwrites
            boolean expectedReturnValue = this.reBroadcast || this.testCase == TestCase.INIT;
            doAddAndVerify(this.persistableNetworkPayload, expectedReturnValue, false);
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

        @Test
        public void invalidHash() {
            PersistableNetworkPayload persistableNetworkPayload = new PersistableNetworkPayloadStub(false);

            doAddAndVerify(persistableNetworkPayload, false, false);
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

        @Test
        public void outOfTolerance() {
            PersistableNetworkPayload persistableNetworkPayload = new DateTolerantPayloadStub(false);

            // The onMessage path checks for tolerance
            boolean expectedReturn = this.testCase != TestCase.ON_MESSAGE;

            doAddAndVerify(persistableNetworkPayload, expectedReturn, expectedReturn);
        }
    }
}
