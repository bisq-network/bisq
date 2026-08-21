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

package bisq.core.trade.protocol.bisq_v1.messages;

import bisq.core.btc.model.RawTransactionInput;

import bisq.network.p2p.NodeAddress;

import java.util.List;

import javax.annotation.Nullable;

import static bisq.core.util.Validator.checkList;
import static bisq.core.util.Validator.checkNonBlankString;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

final class TradeMessageValidator {
    private static final int MAX_PORT = 65535;
    // Bitcoin transaction output indices (vout) are unsigned 32-bit integers with valid range 0–4,294,967,295
    private static final long MAX_RAW_INPUT_INDEX = 0xFFFFFFFFL; // (4,294,967,295)

    private TradeMessageValidator() {
    }

    static NodeAddress checkNodeAddress(NodeAddress nodeAddress, String fieldName) {
        checkNotNull(nodeAddress, "Node address cannot be null");
        checkNonBlankString(nodeAddress.getHostName(), fieldName + ".hostName");
        checkArgument(nodeAddress.getPort() > 0 && nodeAddress.getPort() <= MAX_PORT,
                "%s.port must be between 1 and %s", fieldName, MAX_PORT);
        return nodeAddress;
    }

    static void checkNullableNodeAddress(@Nullable NodeAddress nodeAddress, String fieldName) {
        if (nodeAddress != null) {
            checkNodeAddress(nodeAddress, fieldName);
        }
    }

    static void checkNodeAddressList(List<NodeAddress> nodeAddresses, boolean requireNonEmpty, String fieldName) {
        checkList(nodeAddresses, requireNonEmpty, fieldName);
        for (int i = 0; i < nodeAddresses.size(); i++) {
            checkNodeAddress(nodeAddresses.get(i), fieldName + "[" + i + "]");
        }
    }

    static void checkNullableNonBlankString(@Nullable String value, String fieldName) {
        if (value != null) {
            checkNonBlankString(value, fieldName);
        }
    }

    static void checkRawTransactionInputList(List<RawTransactionInput> rawTransactionInputs,
                                             boolean requireNonEmpty,
                                             String fieldName) {
        checkList(rawTransactionInputs, requireNonEmpty, fieldName);
        for (int i = 0; i < rawTransactionInputs.size(); i++) {
            checkRawTransactionInput(rawTransactionInputs.get(i), fieldName + "[" + i + "]");
        }
    }

    private static void checkRawTransactionInput(RawTransactionInput rawTransactionInput, String fieldName) {
        checkNotNull(rawTransactionInput, "%s must not be null", fieldName);
        checkArgument(rawTransactionInput.getIndex() >= 0 && rawTransactionInput.getIndex() <= MAX_RAW_INPUT_INDEX,
                "%s.index must be between 0 and %s", fieldName, MAX_RAW_INPUT_INDEX);
        checkNonEmptyBytes(rawTransactionInput.getParentTransaction(), fieldName + ".parentTransaction");
        checkArgument(rawTransactionInput.getValue() >= 0, "%s.value must not be negative", fieldName);
        checkArgument(rawTransactionInput.getScriptTypeId() >= 0,
                "%s.scriptTypeId must not be negative", fieldName);
    }
}
