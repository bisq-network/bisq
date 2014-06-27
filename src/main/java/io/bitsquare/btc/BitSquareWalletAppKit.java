package io.bitsquare.btc;

import com.google.bitcoin.core.*;
import com.google.bitcoin.kits.WalletAppKit;
import com.google.bitcoin.net.discovery.DnsDiscovery;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.SPVBlockStore;
import com.google.bitcoin.store.WalletProtobufSerializer;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class BitSquareWalletAppKit extends WalletAppKit
{

    public BitSquareWalletAppKit(NetworkParameters params, File directory, String filePrefix)
    {
        super(params, directory, filePrefix);
    }

    @Override
    protected void startUp() throws Exception
    {
        // Runs in a separate thread.
        if (!directory.exists())
        {
            if (!directory.mkdir())
            {
                throw new IOException("Could not create named directory.");
            }
        }
        FileInputStream walletStream = null;
        try
        {
            File chainFile = new File(directory, filePrefix + ".spvchain");
            boolean chainFileExists = chainFile.exists();
            vWalletFile = new File(directory, filePrefix + ".wallet");
            boolean shouldReplayWallet = vWalletFile.exists() && !chainFileExists;

            vStore = new SPVBlockStore(params, chainFile);
            if (!chainFileExists && checkpoints != null)
            {
                // Ugly hack! We have to create the wallet once here to learn the earliest key time, and then throw it
                // away. The reason is that wallet extensions might need access to peergroups/chains/etc so we have to
                // create the wallet later, but we need to know the time early here before we create the BlockChain
                // object.
                long time = Long.MAX_VALUE;
                if (vWalletFile.exists())
                {
                    Wallet wallet = new BitSquareWallet(params);
                    FileInputStream stream = new FileInputStream(vWalletFile);
                    new WalletProtobufSerializer().readWallet(WalletProtobufSerializer.parseToProto(stream), wallet);
                    time = wallet.getEarliestKeyCreationTime();
                }
                CheckpointManager.checkpoint(params, checkpoints, vStore, time);
            }
            vChain = new BlockChain(params, vStore);
            vPeerGroup = createPeerGroup();

            vPeerGroup.setBloomFilterFalsePositiveRate(0.001);  // 0,1% instead of default 0,05%

            if (this.userAgent != null)
                vPeerGroup.setUserAgent(userAgent, version);
            if (vWalletFile.exists())
            {
                walletStream = new FileInputStream(vWalletFile);
                vWallet = new BitSquareWallet(params);
                addWalletExtensions(); // All extensions must be present before we deserialize
                new WalletProtobufSerializer().readWallet(WalletProtobufSerializer.parseToProto(walletStream), vWallet);
                if (shouldReplayWallet)
                    vWallet.clearTransactions(0);
            }
            else
            {
                vWallet = new BitSquareWallet(params);
                vWallet.addKey(new ECKey());
                addWalletExtensions();
            }
            if (useAutoSave) vWallet.autosaveToFile(vWalletFile, 1, TimeUnit.SECONDS, null);
            // Set up peer addresses or discovery first, so if wallet extensions try to broadcast a transaction
            // before we're actually connected the broadcast waits for an appropriate number of connections.
            if (peerAddresses != null)
            {
                for (PeerAddress addr : peerAddresses) vPeerGroup.addAddress(addr);
                peerAddresses = null;
            }
            else
            {
                vPeerGroup.addPeerDiscovery(new DnsDiscovery(params));
            }
            vChain.addWallet(vWallet);
            vPeerGroup.addWallet(vWallet);
            onSetupCompleted();

            if (blockingStartup)
            {
                vPeerGroup.startAsync();
                vPeerGroup.awaitRunning();
                // Make sure we shut down cleanly.
                installShutdownHook();
                // TODO: Be able to use the provided download listener when doing a blocking startup.
                final DownloadListener listener = new DownloadListener();
                vPeerGroup.startBlockChainDownload(listener);
                listener.await();
            }
            else
            {
                Futures.addCallback(vPeerGroup.start(), new FutureCallback<State>()
                {
                    @Override
                    public void onSuccess(State result)
                    {
                        final PeerEventListener l = downloadListener == null ? new DownloadListener() : downloadListener;
                        vPeerGroup.startBlockChainDownload(l);
                    }

                    @Override
                    public void onFailure(Throwable t)
                    {
                        throw new RuntimeException(t);
                    }
                });
            }
        } catch (BlockStoreException e)
        {
            throw new IOException(e);
        } finally
        {
            if (walletStream != null) walletStream.close();
        }
    }

    private void installShutdownHook()
    {
        if (autoStop) Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    BitSquareWalletAppKit.this.stopAsync();
                    BitSquareWalletAppKit.this.awaitTerminated();

                } catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
