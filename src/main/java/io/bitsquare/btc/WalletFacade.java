package io.bitsquare.btc;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerEventListener;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.kits.WalletAppKit;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.params.RegTestParams;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wallettemplate.Main;

import java.math.BigInteger;
import java.util.UUID;

public class WalletFacade implements IWalletFacade
{
    private static final Logger log = LoggerFactory.getLogger(WalletFacade.class);
    private NetworkParameters networkParameters;
    private WalletAppKit walletAppKit;

    private BigInteger balance;

    @Inject
    public WalletFacade(NetworkParameters networkParameters, WalletAppKit walletAppKit)
    {
        this.networkParameters = networkParameters;
        this.walletAppKit = walletAppKit;

        balance = new BigInteger("100000000");
    }

    @Override
    public void initWallet(PeerEventListener peerEventListener)
    {
        if (networkParameters == RegTestParams.get())
        {
            walletAppKit.connectToLocalHost();   // You should run a regtest mode bitcoind locally.
        }
        else if (networkParameters == MainNetParams.get())
        {
            // Checkpoints are block headers that ship inside our app: for a new user, we pick the last header
            // in the checkpoints file and then download the rest from the network. It makes things much faster.
            // Checkpoint files are made using the BuildCheckpoints tool and usually we have to download the
            // last months worth or more (takes a few seconds).
            walletAppKit.setCheckpoints(getClass().getResourceAsStream("/wallet/checkpoints"));
        }

        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        walletAppKit.setDownloadListener(peerEventListener)
                .setBlockingStartup(false)
                .setUserAgent("BitSquare", "1.0");
        walletAppKit.startAsync();
        walletAppKit.awaitRunning();
        // Don't make the user wait for confirmations for now, as the intention is they're sending it their own money!
        walletAppKit.wallet().allowSpendingUnconfirmedTransactions();
        walletAppKit.peerGroup().setMaxConnections(11);

        log.info(walletAppKit.wallet().toString());
    }

    @Override
    public void terminateWallet()
    {
        walletAppKit.stopAsync();
        walletAppKit.awaitTerminated();
    }

    @Override
    public BigInteger getBalance()
    {
        return walletAppKit.wallet().getBalance(Wallet.BalanceType.ESTIMATED);
    }

    @Override
    public String getAddress()
    {
        return walletAppKit.wallet().getKeys().get(0).toAddress(networkParameters).toString();
    }

    @Override
    public boolean pay(BigInteger satoshisToPay, String destinationAddress)
    {
        if (getBalance().subtract(satoshisToPay).longValue() > 0)
        {
            log.info("Pay " + satoshisToPay.toString() + " Satoshis to " + destinationAddress);
            return true;
        }
        else
        {
            log.warn("Not enough funds in wallet for paying " + satoshisToPay.toString() + " Satoshis.");
            return false;
        }

    }

    @Override
    public KeyPair createNewAddress()
    {
        //MOCK
        return new KeyPair(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }
}
