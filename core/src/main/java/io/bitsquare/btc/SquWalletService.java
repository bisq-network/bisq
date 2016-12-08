/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc;

import com.google.common.base.Preconditions;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.common.handlers.ExceptionHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.user.Preferences;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.DeterministicSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * WalletService handles all non trade specific wallet and bitcoin related services.
 * It startup the wallet app kit and initialized the wallet.
 */
public class SquWalletService extends WalletService {
    private static final Logger log = LoggerFactory.getLogger(SquWalletService.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SquWalletService(WalletSetup walletSetup,
                            Preferences preferences,
                            FeeService feeService) {
        super(walletSetup,
                preferences,
                feeService);

        walletSetup.addSetupCompletedHandler(() -> {
            wallet = walletSetup.getTokenWallet();
            wallet.addEventListener(walletEventListener);
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    public String exportWalletData(boolean includePrivKeys) {
        StringBuilder addressEntryListData = new StringBuilder();
        return "BitcoinJ SQU wallet:\n" +
                wallet.toString(includePrivKeys, true, true, walletSetup.chain()) + "\n\n" +
                "SQU address entry list:\n" +
                addressEntryListData.toString() +
                "All pubkeys as hex:\n" +
                wallet.printAllPubKeysAsHex();
    }

    //TODO
    public void restoreSeedWords(DeterministicSeed seed, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
       /* Context ctx = Context.get();
        new Thread(() -> {
            try {
                Context.propagate(ctx);
                walletAppKit.stopAsync();
                walletAppKit.awaitTerminated();
                initialize(seed, resultHandler, exceptionHandler);
            } catch (Throwable t) {
                t.printStackTrace();
                log.error("Executing task failed. " + t.getMessage());
            }
        }, "RestoreWallet-%d").start();*/
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Withdrawal Send
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected Wallet.SendRequest getSendRequest(String fromAddress,
                                                String toAddress,
                                                Coin amount,
                                                Coin fee,
                                                @Nullable KeyParameter aesKey,
                                                AddressEntry.Context context) throws AddressFormatException,
            AddressEntryException, InsufficientMoneyException {
        Transaction tx = new Transaction(params);
        Preconditions.checkArgument(Restrictions.isAboveDust(amount, fee),
                "The amount is too low (dust limit).");
        tx.addOutput(amount.subtract(fee), new Address(params, toAddress));

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.fee = fee;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.aesKey = aesKey;
        sendRequest.shuffleOutputs = false;
        /*Optional<AddressEntry> addressEntry = findAddressEntry(fromAddress, context);
        if (!addressEntry.isPresent())
            throw new AddressEntryException("WithdrawFromAddress is not found in our wallet.");

        checkNotNull(addressEntry.get(), "addressEntry.get() must not be null");
        checkNotNull(addressEntry.get().getAddress(), "addressEntry.get().getAddress() must not be null");
        sendRequest.coinSelector = new TradeWalletCoinSelector(params, addressEntry.get().getAddress());
        sendRequest.changeAddress = addressEntry.get().getAddress();*/
        return sendRequest;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Util
    ///////////////////////////////////////////////////////////////////////////////////////////


}
