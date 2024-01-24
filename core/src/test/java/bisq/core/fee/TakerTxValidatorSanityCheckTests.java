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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.List;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class TakerTxValidatorSanityCheckTests {
    private final List<String> FEE_RECEIVER_ADDRESSES = List.of("2MzBNTJDjjXgViKBGnatDU3yWkJ8pJkEg9w");

    private TxValidator txValidator;

    @BeforeEach
    void setup(@Mock DaoStateService daoStateService, @Mock FilterManager filterManager) {
        String txId = "e3607e971ead7d03619e3a9eeaa771ed5adba14c448839e0299f857f7bb4ec07";
        txValidator = new TxValidator(daoStateService, txId, filterManager);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullAndEmptyMempoolResponse(String jsonText) {
        TxValidator txValidator1 = txValidator.parseJsonValidateTakerFeeTx(jsonText, FEE_RECEIVER_ADDRESSES);
        FeeValidationStatus status = txValidator1.getStatus();
        assertThat(status, is(equalTo(FeeValidationStatus.NACK_JSON_ERROR)));
    }

    @Test
    void invalidJsonResponse() {
        String invalidJson = "in\"valid'json',";
        TxValidator txValidator1 = txValidator.parseJsonValidateTakerFeeTx(invalidJson, FEE_RECEIVER_ADDRESSES);

        FeeValidationStatus status = txValidator1.getStatus();
        assertThat(status, is(equalTo(FeeValidationStatus.NACK_JSON_ERROR)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "txid", "vin", "vout"})
    void mempoolResponseWithMissingField(String missingField) {
        JsonObject json = MakerTxValidatorSanityCheckTests.getValidBtcMakerFeeMempoolJsonResponse();
        json.remove(missingField);
        assertThat(json.has(missingField), is(false));

        String jsonContent = new Gson().toJson(json);
        TxValidator txValidator1 = txValidator.parseJsonValidateTakerFeeTx(jsonContent, FEE_RECEIVER_ADDRESSES);

        FeeValidationStatus status = txValidator1.getStatus();
        assertThat(status, is(equalTo(FeeValidationStatus.NACK_JSON_ERROR)));
    }

    @Test
    void mempoolResponseWithoutConfirmedField() {
        JsonObject json = MakerTxValidatorSanityCheckTests.getValidBtcMakerFeeMempoolJsonResponse();
        json.get("status").getAsJsonObject().remove("confirmed");
        assertThat(json.get("status").getAsJsonObject().has("confirmed"), is(false));

        String jsonContent = new Gson().toJson(json);
        TxValidator txValidator1 = txValidator.parseJsonValidateTakerFeeTx(jsonContent, FEE_RECEIVER_ADDRESSES);

        FeeValidationStatus status = txValidator1.getStatus();
        assertThat(status, is(equalTo(FeeValidationStatus.NACK_JSON_ERROR)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vin", "vout"})
    void checkFeeAddressBtcTestVinOrVoutNotJsonArray(String vinOrVout) {
        JsonObject json = MakerTxValidatorSanityCheckTests.getValidBtcMakerFeeMempoolJsonResponse();
        json.add(vinOrVout, new JsonPrimitive(1234));
        assertThrows(IllegalStateException.class, () -> json.get(vinOrVout).getAsJsonArray());

        String jsonContent = new Gson().toJson(json);
        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(jsonContent,
                MakerTxValidatorSanityCheckTests.FEE_RECEIVER_ADDRESSES);

        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.NACK_JSON_ERROR));
    }

    @Test
    void checkFeeAddressBtcNoTooFewVin() {
        JsonObject json = MakerTxValidatorSanityCheckTests.getValidBtcMakerFeeMempoolJsonResponse();
        json.add("vin", new JsonArray(0));
        assertThat(json.get("vin").getAsJsonArray().size(), is(0));

        String jsonContent = new Gson().toJson(json);
        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(jsonContent,
                MakerTxValidatorSanityCheckTests.FEE_RECEIVER_ADDRESSES);

        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.NACK_JSON_ERROR));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void checkFeeAddressBtcNoTooFewVout(int numberOfVouts) {
        JsonObject json = MakerTxValidatorSanityCheckTests.getValidBtcMakerFeeMempoolJsonResponse();

        var jsonArray = new JsonArray(numberOfVouts);
        for (int i = 0; i < numberOfVouts; i++) {
            jsonArray.add(i);
        }
        json.add("vout", jsonArray);
        assertThat(json.get("vout").getAsJsonArray().size(), is(numberOfVouts));

        String jsonContent = new Gson().toJson(json);
        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(jsonContent,
                MakerTxValidatorSanityCheckTests.FEE_RECEIVER_ADDRESSES);

        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.NACK_JSON_ERROR));
    }

    @Test
    void checkFeeAmountMissingVinPreVout() {
        JsonObject json = MakerTxValidatorSanityCheckTests.getValidBtcMakerFeeMempoolJsonResponse();
        JsonObject firstInput = json.get("vin").getAsJsonArray().get(0).getAsJsonObject();
        firstInput.remove("prevout");

        boolean hasPreVout = json.get("vin").getAsJsonArray()
                .get(0).getAsJsonObject()
                .has("prevout");
        assertThat(hasPreVout, is(false));

        String jsonContent = new Gson().toJson(json);
        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(jsonContent,
                MakerTxValidatorSanityCheckTests.FEE_RECEIVER_ADDRESSES);

        assertThat(txValidator1.getStatus(), is(FeeValidationStatus.NACK_JSON_ERROR));
    }

    @Test
    void responseHasDifferentTxId() {
        String differentTxId = "abcde971ead7d03619e3a9eeaa771ed5adba14c448839e0299f857f7bb4ec07";

        JsonObject json = MakerTxValidatorSanityCheckTests.getValidBtcMakerFeeMempoolJsonResponse();
        json.add("txid", new JsonPrimitive(differentTxId));
        assertThat(json.get("txid").getAsString(), is(differentTxId));

        String jsonContent = new Gson().toJson(json);
        TxValidator txValidator1 = txValidator.parseJsonValidateTakerFeeTx(jsonContent, FEE_RECEIVER_ADDRESSES);

        FeeValidationStatus status = txValidator1.getStatus();
        assertThat(status, is(equalTo(FeeValidationStatus.NACK_JSON_ERROR)));
    }
}
