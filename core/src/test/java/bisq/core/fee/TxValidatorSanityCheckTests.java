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

import java.net.URL;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.IOException;

import java.util.List;
import java.util.Objects;

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

@ExtendWith(MockitoExtension.class)
public class TxValidatorSanityCheckTests {
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
        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(jsonText, FEE_RECEIVER_ADDRESSES);
        FeeValidationStatus status = txValidator1.getStatus();
        assertThat(status, is(equalTo(FeeValidationStatus.NACK_JSON_ERROR)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "txid"})
    void mempoolResponseWithMissingField(String missingField) throws IOException {
        JsonObject json = getValidBtcMakerFeeMempoolJsonResponse();
        json.remove(missingField);
        assertThat(json.has(missingField), is(false));

        String jsonContent = new Gson().toJson(json);
        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(jsonContent, FEE_RECEIVER_ADDRESSES);

        FeeValidationStatus status = txValidator1.getStatus();
        assertThat(status, is(equalTo(FeeValidationStatus.NACK_JSON_ERROR)));
    }

    @Test
    void mempoolResponseWithoutConfirmedField() throws IOException {
        JsonObject json = getValidBtcMakerFeeMempoolJsonResponse();
        json.get("status").getAsJsonObject().remove("confirmed");
        assertThat(json.get("status").getAsJsonObject().has("confirmed"), is(false));

        String jsonContent = new Gson().toJson(json);
        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(jsonContent, FEE_RECEIVER_ADDRESSES);

        FeeValidationStatus status = txValidator1.getStatus();
        assertThat(status, is(equalTo(FeeValidationStatus.NACK_JSON_ERROR)));
    }

    @Test
    void responseHasDifferentTxId() throws IOException {
        String differentTxId = "abcde971ead7d03619e3a9eeaa771ed5adba14c448839e0299f857f7bb4ec07";

        JsonObject json = getValidBtcMakerFeeMempoolJsonResponse();
        json.add("txid", new JsonPrimitive(differentTxId));
        assertThat(json.get("txid").getAsString(), is(differentTxId));

        String jsonContent = new Gson().toJson(json);
        TxValidator txValidator1 = txValidator.parseJsonValidateMakerFeeTx(jsonContent, FEE_RECEIVER_ADDRESSES);

        FeeValidationStatus status = txValidator1.getStatus();
        assertThat(status, is(equalTo(FeeValidationStatus.NACK_JSON_ERROR)));
    }

    private JsonObject getValidBtcMakerFeeMempoolJsonResponse() throws IOException {
        URL resource = getClass().getClassLoader().getResource("mempool_test_data/valid_btc_maker_fee.json");
        String path = Objects.requireNonNull(resource).getPath();
        String jsonContent = Files.readString(Path.of(path));
        return new Gson().fromJson(jsonContent, JsonObject.class);
    }
}
