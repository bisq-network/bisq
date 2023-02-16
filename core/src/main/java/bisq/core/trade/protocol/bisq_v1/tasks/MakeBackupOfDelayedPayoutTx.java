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

package bisq.core.trade.protocol.bisq_v1.tasks;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.support.DelayedPayoutRecoveryPayload;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.common.taskrunner.TaskRunner;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.security.AlgorithmParameters;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class MakeBackupOfDelayedPayoutTx extends TradeTask {
    public MakeBackupOfDelayedPayoutTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            SecretKeySpec secret = new SecretKeySpec(btcWalletService.getHashOfRecoveryPhrase(), "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secret);
            AlgorithmParameters params = cipher.getParameters();
            byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
            byte[] encryptedBytes = cipher.doFinal(checkNotNull(trade.getDelayedPayoutTxBytes()));
            processModel.getP2PService().addProtectedStorageEntry(new DelayedPayoutRecoveryPayload(
                    (int) trade.getLockTime(),
                    encryptedBytes,
                    iv,
                    processModel.getKeyRing().getPubKeyRing().getSignaturePubKey(), null));

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
