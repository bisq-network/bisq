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

package bisq.core.fee;

import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.FilterManager;
import bisq.core.provider.mempool.FeeValidationStatus;
import bisq.core.provider.mempool.TxValidator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Collections;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
public class MakerInvalidFeeBtcValidationTests {
    private TxValidator txValidator;

    @BeforeEach
    void setup(@Mock DaoStateService daoStateService, @Mock FilterManager filterManager) {
        String txId = "e3607e971ead7d03619e3a9eeaa771ed5adba14c448839e0299f857f7bb4ec07";
        txValidator = new TxValidator(daoStateService, txId, filterManager);
    }

    @Test
    void makerCheckFeeAddressBtcInvalidFeeAddress() {
        JsonObject json = MakerTxValidatorSanityCheckTests.getValidBtcMakerFeeMempoolJsonResponse();
        json.get("status").getAsJsonObject().add("block_height", new JsonPrimitive(600_000));

        var newBlockHeight = json.get("status").getAsJsonObject().get("block_height").getAsInt();
        assertThat(newBlockHeight, is(600_000));

        String jsonContent = new Gson().toJson(json);
        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(jsonContent, Collections.emptyList());
        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.NACK_UNKNOWN_FEE_RECEIVER));
    }
}
