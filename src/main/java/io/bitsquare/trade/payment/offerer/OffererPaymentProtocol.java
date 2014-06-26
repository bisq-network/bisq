package io.bitsquare.trade.payment.offerer;

import com.google.bitcoin.core.*;
import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.TradeMessage;
import io.bitsquare.msg.TradeMessageType;
import io.bitsquare.msg.listeners.TradeMessageListener;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.user.User;
import io.bitsquare.util.Utilities;
import javafx.util.Pair;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;

import static com.google.common.base.Preconditions.checkNotNull;

//TODO refactor to process based pattern
public class OffererPaymentProtocol
{

    private static final Logger log = LoggerFactory.getLogger(OffererPaymentProtocol.class);

    private Trade trade;
    private Offer offer;
    private Contract contract;
    private OffererPaymentProtocolListener offererPaymentProtocolListener;
    private MessageFacade messageFacade;
    private WalletFacade walletFacade;
    private BlockChainFacade blockChainFacade;
    private CryptoFacade cryptoFacade;
    private User user;
    private PeerAddress peerAddress;
    private boolean isTakeOfferRequested;
    private int numberOfSteps = 10;//TODO
    private int currentStep = 0;
    private String preparedOffererDepositTxAsHex;
    private Transaction depositTransaction;
    private String takerPayoutAddress;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OffererPaymentProtocol(Trade trade,
                                  OffererPaymentProtocolListener offererPaymentProtocolListener,
                                  MessageFacade messageFacade,
                                  WalletFacade walletFacade,
                                  BlockChainFacade blockChainFacade,
                                  CryptoFacade cryptoFacade,
                                  User user)
    {
        checkNotNull(trade);
        checkNotNull(messageFacade);
        checkNotNull(walletFacade);
        checkNotNull(blockChainFacade);
        checkNotNull(cryptoFacade);
        checkNotNull(user);

        this.trade = trade;
        this.offererPaymentProtocolListener = offererPaymentProtocolListener;
        this.messageFacade = messageFacade;
        this.walletFacade = walletFacade;
        this.blockChainFacade = blockChainFacade;
        this.cryptoFacade = cryptoFacade;
        this.user = user;

        offer = trade.getOffer();

        messageFacade.addOffererPaymentProtocol(this);

        log.debug("0 Constr");
    }


    //************************************************************************************************
    // 1.1-1.2 Takers tasks, we start when we get the take offer request
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 1.3
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We got a take offer request and check if the offer is not already reserved for another user.
    // If the offer is free we send an accept message.

    public void onTakeOfferRequested(PeerAddress sender)
    {
        log.debug("1.3 onTakeOfferRequested");

        peerAddress = sender;
        // The offer must be still available.
        // TODO check also if offer is still in our offer list, if we canceled recently there might be inconsistency
        if (!isTakeOfferRequested)
        {
            log.debug("1.3 offer not yet requested");
            TradeMessageListener listener = new TradeMessageListener()
            {
                @Override
                public void onResult()
                {
                    log.debug("1.3 onTakeOfferRequested ACCEPT_TAKE_OFFER_REQUEST onResult");
                    // The accept message has arrived at the peer
                    // We set requested flag and remove the offer from the orderbook
                    offererPaymentProtocolListener.onProgress(getProgress());

                    isTakeOfferRequested = true;
                    try
                    {
                        log.debug("1.3 messageFacade.removeOffer");
                        messageFacade.removeOffer(offer);
                    } catch (IOException e)
                    {
                        log.error("1.3 messageFacade.removeOffer failed " + e.getMessage());
                        offererPaymentProtocolListener.onFailure("removeOffer failed " + e.getMessage());
                    }

                    // It's the takers turn again, so we are in wait mode....
                }

                @Override
                public void onFailed()
                {
                    log.warn("1.3 onTakeOfferRequested ACCEPT_TAKE_OFFER_REQUEST onFailed");
                    offererPaymentProtocolListener.onFailure("onTakeOfferRequested onSendTradingMessageFailed");
                }
            };

            offererPaymentProtocolListener.onProgress(getProgress());

            // 1.3a Send accept take offer message
            TradeMessage tradeMessage = new TradeMessage(TradeMessageType.ACCEPT_TAKE_OFFER_REQUEST, trade.getId());
            messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);
        }
        else
        {
            log.debug("1.3 offer already requested REJECT_TAKE_OFFER_REQUEST");
            TradeMessage tradeMessage = new TradeMessage(TradeMessageType.REJECT_TAKE_OFFER_REQUEST, trade.getId());
            TradeMessageListener listener = new TradeMessageListener()
            {
                @Override
                public void onResult()
                {
                    log.debug("1.3 isTakeOfferRequested REJECT_TAKE_OFFER_REQUEST onResult");
                    // no more steps are needed, as we are not interested in that trade
                }

                @Override
                public void onFailed()
                {
                    log.warn("1.3 isTakeOfferRequested REJECT_TAKE_OFFER_REQUEST onFailed");
                    // we can ignore that as we are not interested in that trade
                }
            };

            // Offerer reject take offer because it's not available anymore
            messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);
        }
    }


    //************************************************************************************************
    // 1.4, 2.1 - 2.2 Takers task, we wait until the next incoming message
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.3
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onTakeOfferFeePayed(TradeMessage requestTradeMessage)
    {
        log.debug("2.3 onTakeOfferFeePayed");
        trade.setTakeOfferFeeTxID(requestTradeMessage.getTakeOfferFeeTxID());
        trade.setTradeAmount(requestTradeMessage.getTradeAmount());
        verifyTakeOfferFeePayment(requestTradeMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.4
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void verifyTakeOfferFeePayment(TradeMessage requestTradeMessage)
    {
        log.debug("2.4 verifyTakeOfferFeePayment");
        //TODO just dummy now, will be async
        int numOfPeersSeenTx = walletFacade.getNumOfPeersSeenTx(requestTradeMessage.getTakeOfferFeeTxID());
        if (numOfPeersSeenTx > 2)
        {
            createDepositTx(requestTradeMessage);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.5
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createDepositTx(TradeMessage requestTradeMessage)
    {
        checkNotNull(requestTradeMessage);

        log.debug("2.5 createDepositTx");

        BigInteger offererInputAmount = trade.getCollateralAmount();
        String offererPubKey = walletFacade.getAddressInfoByTradeID(trade.getId()).getPubKeyAsHexString();
        String takerPubKey = requestTradeMessage.getTakerMultiSigPubKey();
        String arbitratorPubKey = offer.getArbitrator().getPubKeyAsHex();

        checkNotNull(requestTradeMessage.getTakerMultiSigPubKey());
        checkNotNull(offererPubKey);
        checkNotNull(takerPubKey);
        checkNotNull(arbitratorPubKey);

        log.debug("2.5 offererCreatesMSTxAndAddPayment");
        log.debug("offererInputAmount     " + BtcFormatter.satoshiToString(offererInputAmount));
        log.debug("offerer pubkey    " + offererPubKey);
        log.debug("taker pubkey      " + takerPubKey);
        log.debug("arbitrator pubkey " + arbitratorPubKey);
        try
        {
            Transaction tx = walletFacade.offererCreatesMSTxAndAddPayment(offererInputAmount, offererPubKey, takerPubKey, arbitratorPubKey, trade.getId());
            preparedOffererDepositTxAsHex = Utils.bytesToHexString(tx.bitcoinSerialize());
            long offererTxOutIndex = tx.getInput(0).getOutpoint().getIndex();
            log.debug("2.5 deposit tx created: " + tx);
            log.debug("2.5 deposit txAsHex: " + preparedOffererDepositTxAsHex);
            sendDepositTxAndDataForContract(preparedOffererDepositTxAsHex, offererPubKey, offererTxOutIndex);
        } catch (InsufficientMoneyException e)
        {
            log.warn("2.5 InsufficientMoneyException " + e.getMessage());
            //110010000
            // 20240000
        }

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.6
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void sendDepositTxAndDataForContract(String preparedOffererDepositTxAsHex, String offererPubKey, long offererTxOutIndex)
    {
        log.debug("2.6 sendDepositTxAndDataForContract");
        // Send all the requested data

        TradeMessageListener listener = new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.debug("2.6 sendDepositTxAndDataForContract onResult");
                // Message arrived at taker
                offererPaymentProtocolListener.onProgress(getProgress());

                // We wait until we get the signed tx back
            }

            @Override
            public void onFailed()
            {
                log.debug("2.6 sendDepositTxAndDataForContract onFailed");
                offererPaymentProtocolListener.onFailure("sendDepositTxAndDataForContract onSendTradingMessageFailed");
            }
        };

        offererPaymentProtocolListener.onProgress(getProgress());

        BankAccount bankAccount = user.getBankAccount(offer.getBankAccountUID());
        String accountID = user.getAccountID();

        checkNotNull(trade.getId());
        checkNotNull(bankAccount);
        checkNotNull(accountID);
        checkNotNull(preparedOffererDepositTxAsHex);

        TradeMessage tradeMessage = new TradeMessage(TradeMessageType.REQUEST_TAKER_DEPOSIT_PAYMENT, trade.getId(), bankAccount, accountID, offererPubKey, preparedOffererDepositTxAsHex, offererTxOutIndex);
        log.debug("2.6 sendTradingMessage");
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);
    }

    //************************************************************************************************
    // 2.7 - 2.11 Takers task, we wait until the next incoming message
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.1 Incoming msg from taker
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onDepositTxReadyForPublication(TradeMessage requestTradeMessage)
    {
        log.debug("3.1 onDepositTxReadyForPublication");
        takerPayoutAddress = requestTradeMessage.getTakerPayoutAddress();
        verifyTaker(requestTradeMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.2  Verify offerers account registration and against the blacklist
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void verifyTaker(TradeMessage requestTradeMessage)
    {
        log.debug("3.2 verifyTaker");
        log.debug("3.2.1 verifyAccountRegistration");
        if (blockChainFacade.verifyAccountRegistration())
        {
            log.debug("3.2.2 isAccountBlackListed");
            if (!blockChainFacade.isAccountBlackListed(requestTradeMessage.getAccountID(), requestTradeMessage.getBankAccount()))
            {
                verifyAndSignContract(requestTradeMessage);
            }
            else
            {
                offererPaymentProtocolListener.onFailure("Taker is blacklisted.");
            }
        }
        else
        {
            offererPaymentProtocolListener.onFailure("Takers account registration is invalid.");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.3  Verify and sign the contract
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void verifyAndSignContract(TradeMessage requestTradeMessage)
    {
        contract = new Contract(offer,
                trade.getTradeAmount(),
                trade.getTakeOfferFeeTxID(),
                user.getAccountID(),
                requestTradeMessage.getAccountID(),
                user.getCurrentBankAccount(),
                requestTradeMessage.getBankAccount(),
                offer.getMessagePubKeyAsHex(),
                requestTradeMessage.getTakerMessagePubKey());

        log.debug("3.3 offerer contract created: " + contract.toString());

        String contractAsJson = Utilities.objectToJson(contract);
        // log.debug("3.3 contractAsJson: " + contractAsJson);
        log.debug("3.3 requestTradingMessage.getContractAsJson(): " + requestTradeMessage.getContractAsJson());

        if (contractAsJson.equals(requestTradeMessage.getContractAsJson()))
        {
            log.debug("3.3 The 2 contracts as json does  match");
            String signature = cryptoFacade.signContract(walletFacade.getRegistrationAddressInfo().getKey(), contractAsJson);
            trade.setContract(contract);
            trade.setContractAsJson(contractAsJson);
            trade.setContractTakerSignature(signature);
            log.debug("3.3 signature: " + signature);

            signAndPublishDepositTx(requestTradeMessage);
        }
        else
        {
            log.error("3.3 verifyContract failed");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.4  Sign and publish the deposit tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void signAndPublishDepositTx(TradeMessage requestTradeMessage)
    {
        log.debug("3.4 signAndPublishDepositTx");

        String signedTakerDepositTxAsHex = requestTradeMessage.getSignedTakerDepositTxAsHex();
        String txConnOutAsHex = requestTradeMessage.getTxConnOutAsHex();
        String txScriptSigAsHex = requestTradeMessage.getTxScriptSigAsHex();

        FutureCallback<Transaction> callback = new FutureCallback<Transaction>()
        {
            @Override
            public void onSuccess(Transaction transaction)
            {
                log.info("3.4 signAndPublishDepositTx offererSignAndSendTx onSuccess:" + transaction.toString());

                String txID = transaction.getHashAsString();
                trade.setDepositTransaction(transaction);
                log.debug("3.4 deposit tx published: " + transaction);
                log.debug("3.4 deposit txID: " + txID);
                sendDepositTxIdToTaker(transaction);
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.error("3.4 signAndPublishDepositTx offererSignAndSendTx onFailure:" + t.getMessage());
            }
        };
        try
        {
            log.debug("3.4 offererSignAndSendTx");
            depositTransaction = walletFacade.offererSignAndPublishTx(preparedOffererDepositTxAsHex, signedTakerDepositTxAsHex, txConnOutAsHex, txScriptSigAsHex,
                    requestTradeMessage.getOffererTxOutIndex(), requestTradeMessage.getTakerTxOutIndex(), callback);
        } catch (Exception e)
        {
            log.error("3.4 error at walletFacade.offererSignAndSendTx: " + e.getMessage());
            e.getStackTrace();// Could not understand form of connected output script: RETURN
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.5  Send tx id of published deposit tx to taker
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void sendDepositTxIdToTaker(Transaction transaction)
    {
        log.debug("3.5 sendDepositTxIdToTaker");

        TradeMessageListener listener = new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.debug("3.5 sendDepositTxIdToTaker DEPOSIT_TX_PUBLISHED onResult");
                offererPaymentProtocolListener.onProgress(getProgress());
            }

            @Override
            public void onFailed()
            {
                log.warn("3.5 sendDepositTxIdToTaker DEPOSIT_TX_PUBLISHED onFailed");
                offererPaymentProtocolListener.onFailure("sendDepositTxAndDataForContract onSendTradingMessageFailed");
            }
        };
        TradeMessage tradeMessage = new TradeMessage(TradeMessageType.DEPOSIT_TX_PUBLISHED, trade.getId(), Utils.bytesToHexString(transaction.bitcoinSerialize()));
        log.debug("3.5  sendTradingMessage");
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);

        // wait for at least 1 confirmation, then pay Fiat
        offererPaymentProtocolListener.onDepositTxPublished(tradeMessage.getDepositTxID());
        setupListenerForBlockChainConfirmation(transaction);
    }

    //************************************************************************************************
    // 3.6 Taker got informed, but no action from his side required.
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.7  We setup a listener for block chain confirmation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupListenerForBlockChainConfirmation(Transaction transaction)
    {
        log.debug("3.7  setupListenerForBlockChainConfirmation");

        // for testing
        // TODO listeners mut be done with blockchain not wallet
        onDepositTxConfirmedInBlockchain();

        transaction.getConfidence().addEventListener(new TransactionConfidence.Listener()
        {
            @Override
            public void onConfidenceChanged(Transaction tx, ChangeReason reason)
            {
                if (reason == ChangeReason.SEEN_PEERS)
                {
                    updateConfirmation(tx.getConfidence());

                    //todo just for testing now, dont like to wait so long...
                   /* if (tx.getConfidenceForAddress().numBroadcastPeers() > 3)
                    {
                        onDepositTxConfirmedInBlockchain();
                        transaction.getConfidenceForAddress().removeEventListener(this);
                    }  */

                }
                if (reason == ChangeReason.TYPE && tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING)
                {
                    onDepositTxConfirmedInBlockchain();
                    transaction.getConfidence().removeEventListener(this);
                }
            }
        }
        );
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.8  We check if the block chain confirmation is >= 1
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateConfirmation(TransactionConfidence confidence)
    {
        log.debug("3.8  updateConfirmation " + confidence.toString());
        offererPaymentProtocolListener.onDepositTxConfirmedUpdate(confidence);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.9  Blockchain confirmation received, so tell user he should start bank transfer
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onDepositTxConfirmedInBlockchain()
    {
        log.debug("3.9  readyForBankTransfer");
        offererPaymentProtocolListener.onDepositTxConfirmedInBlockchain();
    }

    //************************************************************************************************
    // Offerer need to start bank tx, after he done it he call the next step
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.10  User clicked the "bank transfer inited" button, so we tell the peer that we started the bank tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void bankTransferInited()
    {
        log.debug("3.10 bankTransferInited");

        TradeMessageListener listener = new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.debug("3.10 bankTransferInited BANK_TX_INITED onResult");
                offererPaymentProtocolListener.onProgress(getProgress());
            }

            @Override
            public void onFailed()
            {
                log.warn("3.10 bankTransferInited BANK_TX_INITED onFailed");
                offererPaymentProtocolListener.onFailure("bankTransferInited BANK_TX_INITED");
            }
        };


        try
        {
            BigInteger collateral = trade.getCollateralAmount();
            BigInteger offererPaybackAmount = trade.getTradeAmount().add(collateral);
            BigInteger takerPaybackAmount = collateral;

            log.debug("offererPaybackAmount " + offererPaybackAmount.toString());
            log.debug("takerPaybackAmount " + takerPaybackAmount.toString());
            log.debug("depositTransaction.getHashAsString() " + depositTransaction.getHashAsString());
            log.debug("takerPayoutAddress " + takerPayoutAddress);
            log.debug("walletFacade.offererCreatesAndSignsPayoutTx");
            Pair<ECKey.ECDSASignature, String> result = walletFacade.offererCreatesAndSignsPayoutTx(depositTransaction.getHashAsString(), offererPaybackAmount, takerPaybackAmount, takerPayoutAddress, trade.getId());

            ECKey.ECDSASignature offererSignature = result.getKey();
            String offererSignatureR = offererSignature.r.toString();
            String offererSignatureS = offererSignature.s.toString();
            String depositTxAsHex = result.getValue();
            String offererPayoutAddress = walletFacade.getAddressInfoByTradeID(trade.getId()).getAddressString();
            TradeMessage tradeMessage = new TradeMessage(TradeMessageType.BANK_TX_INITED, trade.getId(),
                    depositTxAsHex,
                    offererSignatureR,
                    offererSignatureS,
                    offererPaybackAmount,
                    takerPaybackAmount,
                    offererPayoutAddress);

            log.debug("depositTxAsHex " + depositTxAsHex);
            log.debug("offererSignatureR " + offererSignatureR);
            log.debug("offererSignatureS " + offererSignatureS);
            log.debug("offererPaybackAmount " + offererPaybackAmount.toString());
            log.debug("takerPaybackAmount " + takerPaybackAmount.toString());
            log.debug("offererPayoutAddress " + offererPayoutAddress);

            log.debug("3.10  sendTradingMessage BANK_TX_INITED");
            messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);

        } catch (InsufficientMoneyException e)
        {
            log.error("3.10 offererCreatesAndSignsPayoutTx  onFailed InsufficientMoneyException " + e.getMessage());
        } catch (AddressFormatException e)
        {
            log.error("3.10 offererCreatesAndSignsPayoutTx  onFailed AddressFormatException " + e.getMessage());
        }
    }


    //************************************************************************************************
    // We wait until taker has received the money on his bank account, that might take a while.
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.14  We received the payout tx. Trade is completed
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onPayoutTxPublished(TradeMessage tradeMessage)
    {
        log.debug("3.14  onPayoutTxPublished");
        log.debug("3.14  TRADE COMPLETE!!!!!!!!!!!");

        offererPaymentProtocolListener.onPayoutTxPublished(tradeMessage.getPayoutTxAsHex());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Util
    ///////////////////////////////////////////////////////////////////////////////////////////

    private double getProgress()
    {
        currentStep++;
        return (double) currentStep / (double) numberOfSteps;
    }


}
