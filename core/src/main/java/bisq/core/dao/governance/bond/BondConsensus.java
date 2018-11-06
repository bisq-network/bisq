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

package bisq.core.dao.governance.bond;

import bisq.core.dao.governance.bond.lockup.LockupReason;
import bisq.core.dao.state.model.blockchain.OpReturnType;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BondConsensus {
    // In the UI we don't allow 0 as that would mean that the tx gets spent
    // in the same block as the unspent tx and we don't support unconfirmed txs in the DAO. Technically though 0
    // works as well.
    @Getter
    private static int minLockTime = 1;

    // Max value is max of a short int as we use only 2 bytes in the opReturn for the lockTime
    @Getter
    private static int maxLockTime = 65535;

    public static byte[] getLockupOpReturnData(int lockTime, LockupReason type, byte[] hash) throws IOException {
        // PushData of <= 4 bytes is converted to int when returned from bitcoind and not handled the way we
        // require by btcd-cli4j, avoid opReturns with 4 bytes or less
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(OpReturnType.LOCKUP.getType());
            outputStream.write(Version.LOCKUP);
            outputStream.write(type.getId());
            byte[] bytes = Utilities.integerToByteArray(lockTime, 2);
            outputStream.write(bytes[0]);
            outputStream.write(bytes[1]);
            outputStream.write(hash);
            return outputStream.toByteArray();
        } catch (IOException e) {
            // Not expected to happen ever
            e.printStackTrace();
            log.error(e.toString());
            throw e;
        }
    }

    public static boolean hasOpReturnDataValidLength(byte[] opReturnData) {
        return opReturnData.length == 25;
    }

    public static int getLockTime(byte[] opReturnData) {
        return Utilities.byteArrayToInteger(Arrays.copyOfRange(opReturnData, 3, 5));
    }

    public static byte[] getHashFromOpReturnData(byte[] opReturnData) {
        return Arrays.copyOfRange(opReturnData, 5, 25);
    }

    public static boolean isLockTimeInValidRange(int lockTime) {
        return lockTime >= BondConsensus.getMinLockTime() && lockTime <= BondConsensus.getMaxLockTime();
    }

    public static Optional<LockupReason> getLockupReason(byte[] opReturnData) {
        return LockupReason.getLockupReason(opReturnData[2]);
    }

    public static boolean isLockTimeOver(long unlockBlockHeight, long currentBlockHeight) {
        return currentBlockHeight >= unlockBlockHeight;
    }
}
