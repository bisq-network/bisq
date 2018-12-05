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

package bisq.core.dao.governance.asset;

import bisq.core.dao.governance.param.Param;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.OpReturnType;

import bisq.common.app.Version;
import bisq.common.crypto.Hash;

import org.bitcoinj.core.Coin;

import com.google.common.base.Charsets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AssetConsensus {
    public static Coin getFeePerDay(DaoStateService daoStateService, int chainHeight) {
        return daoStateService.getParamValueAsCoin(Param.ASSET_LISTING_FEE_PER_DAY, chainHeight);
    }

    public static byte[] getHash(StatefulAsset statefulAsset) {
        String stringInput = "AssetListingFee-" + statefulAsset.getTickerSymbol();
        final byte[] bytes = stringInput.getBytes(Charsets.UTF_8);
        return Hash.getSha256Ripemd160hash(bytes);
    }

    public static byte[] getOpReturnData(byte[] hash) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(OpReturnType.ASSET_LISTING_FEE.getType());
            outputStream.write(Version.ASSET_LISTING_FEE);
            outputStream.write(hash);
            return outputStream.toByteArray();
        } catch (IOException e) {
            // Not expected to happen ever
            e.printStackTrace();
            log.error(e.toString());
            return new byte[0];
        }
    }

    public static boolean hasOpReturnDataValidLength(byte[] opReturnData) {
        return opReturnData.length == 22;
    }
}
