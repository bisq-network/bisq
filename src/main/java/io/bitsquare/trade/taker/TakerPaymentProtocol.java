package io.bitsquare.trade.taker;

import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.Fees;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.TradeMessage;
import io.bitsquare.msg.TradeMessageType;
import io.bitsquare.msg.listeners.AddressLookupListener;
import io.bitsquare.msg.listeners.TradeMessageListener;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.user.User;
import io.bitsquare.util.Utilities;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkNotNull;

//TODO refactor to process based pattern
public class TakerPaymentProtocol
{
    private static final Logger log = LoggerFactory.getLogger(TakerPaymentProtocol.class);

    private Trade trade;
    private Offer offer;
    private Contract contract;
    private TakerPaymentProtocolListener takerPaymentProtocolListener;
    private MessageFacade messageFacade;
    private WalletFacade walletFacade;
    private BlockChainFacade blockChainFacade;
    private CryptoFacade cryptoFacade;
    private User user;
    private PeerAddress peerAddress;
    private int numberOfSteps = 10;//TODO
    private int currentStep = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TakerPaymentProtocol(Trade trade,
                                TakerPaymentProtocolListener takerPaymentProtocolListener,
                                MessageFacade messageFacade,
                                WalletFacade walletFacade,
                                BlockChainFacade blockChainFacade,
                                CryptoFacade cryptoFacade,
                                User user)
    {
        this.trade = trade;
        this.takerPaymentProtocolListener = takerPaymentProtocolListener;
        this.messageFacade = messageFacade;
        this.walletFacade = walletFacade;
        this.blockChainFacade = blockChainFacade;
        this.cryptoFacade = cryptoFacade;
        this.user = user;

        offer = trade.getOffer();

        messageFacade.addTakerPaymentProtocol(this);
    }

    public void takeOffer()
    {
        log.debug("1 takeOffer");
        findPeerAddress();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 1.1
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void findPeerAddress()
    {
        log.debug("1.1 findPeerAddress");
        AddressLookupListener addressLookupListener = new AddressLookupListener()
        {
            @Override
            public void onResult(PeerAddress address)
            {
                log.debug("1.1 findPeerAddress onResult");
                // We got the peer address
                peerAddress = address;

                takerPaymentProtocolListener.onProgress(getProgress());

                // next
                requestTakeOffer();
            }

            @Override
            public void onFailed()
            {
                log.debug("1.1 findPeerAddress onFailed");
                takerPaymentProtocolListener.onFailure("onGetPeerAddressFailed");
            }
        };

        takerPaymentProtocolListener.onProgress(getProgress());

        // Request the peers address from the DHT
        messageFacade.getPeerAddress(offer.getMessagePubKeyAsHex(), addressLookupListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 1.2
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void requestTakeOffer()
    {
        log.debug("1.2 requestTakeOffer");
        TradeMessageListener listener = new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.debug("1.2 requestTakeOffer onResult");
                // Our message has arrived
                // We don't know yet if the offerer has accepted
                // the take request (if offer is not already reserved for another user)
                // We await for an incoming message from the offerer with the accept msg
                takerPaymentProtocolListener.onProgress(getProgress());

                // Wait for message from offerer...
            }

            @Override
            public void onFailed()
            {
                log.debug("1.2 requestTakeOffer onFailed");
                takerPaymentProtocolListener.onFailure("sendTakeOfferRequest onSendTradingMessageFailed");
            }
        };

        takerPaymentProtocolListener.onProgress(getProgress());

        // Send the take offer request
        TradeMessage tradeMessage = new TradeMessage(TradeMessageType.REQUEST_TAKE_OFFER, trade.getUid());
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);
    }


    //************************************************************************************************
    // 1.3. Offerers tasks, we are in waiting mode
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 1.4
    ///////////////////////////////////////////////////////////////////////////////////////////


    // 1.4a offerer has accepted the take offer request. Move on to step 2.
    public void onTakeOfferRequestAccepted()
    {
        log.debug("1.4a onTakeOfferRequestAccepted");
        takerPaymentProtocolListener.onProgress(getProgress());

        payOfferFee(trade);
    }

    // 1.4b Offerer has rejected the take offer request. The UI controller will handle the case.
    public void onTakeOfferRequestRejected()
    {
        log.debug("1.4b onTakeOfferRequestRejected");
        takerPaymentProtocolListener.onProgress(getProgress());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.1
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void payOfferFee(Trade trade)
    {
        log.debug("2.1 payOfferFee");
        FutureCallback<Transaction> callback = new FutureCallback<Transaction>()
        {
            @Override
            public void onSuccess(Transaction transaction)
            {
                log.debug("2.1 payOfferFee onSuccess");
                log.info("sendResult onSuccess txid:" + transaction.getHashAsString());

                // Offer fee payed successfully.
                trade.setTakeOfferFeeTxID(transaction.getHashAsString());

                takerPaymentProtocolListener.onProgress(getProgress());

                // move on
                sendTakerOfferFeeTxID(transaction.getHashAsString());
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.debug("2.1 payOfferFee onFailure");
                takerPaymentProtocolListener.onFailure("payOfferFee onFailure " + t.getMessage());
            }
        };
        try
        {
            // Pay the offer fee
            takerPaymentProtocolListener.onProgress(getProgress());
            walletFacade.payOfferFee(Fees.OFFER_TAKER_FEE, callback);
        } catch (InsufficientMoneyException e)
        {
            takerPaymentProtocolListener.onProgress(getProgress());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.2
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Request peers account details. At that moment private data (bank account nr.) of the offerer get visible to the taker.
    // We send also the tx id of the fee payment, so the offerer can confirm that the payment is seen in the network
    // 0 block confirmation is acceptable for the fee to not block the process
    // The offerer will wait until a minimum of peers seen the tx before sending his data.
    // We also get the multisig tx delivered
    private void sendTakerOfferFeeTxID(String takeOfferFeeTxID)
    {
        log.debug("2.2 sendTakerOfferFeeTxID");
        TradeMessageListener listener = new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.debug("2.2 sendTakerOfferFeeTxID onResult");
                // Message has arrived
                takerPaymentProtocolListener.onProgress(getProgress());

                // We wait until the offerer send us the data
            }

            @Override
            public void onFailed()
            {
                log.debug("2.2 sendTakerOfferFeeTxID onFailed");
                takerPaymentProtocolListener.onFailure("requestAccountDetails onSendTradingMessageFailed");
            }
        };

        takerPaymentProtocolListener.onProgress(getProgress());

        // 2.3. send request for the account details and send fee tx id so offerer can verify that the fee has been paid.
        TradeMessage tradeMessage = new TradeMessage(TradeMessageType.TAKE_OFFER_FEE_PAYED,
                trade.getUid(),
                trade.getTradeAmount(),
                takeOfferFeeTxID,
                walletFacade.getPubKeyAsHex());
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);
    }


    //************************************************************************************************
    // 2.3 - 2.6 Offerers tasks, we are in waiting mode
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.7  Incoming msg from offerer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onTakerDepositPaymentRequested(TradeMessage requestTradeMessage)
    {
        log.debug("2.7 onTakerDepositPaymentRequested");
        verifyOfferer(requestTradeMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.8  Verify offerers account registration and against the blacklist
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void verifyOfferer(TradeMessage requestTradeMessage)
    {
        log.debug("2.8 verifyOfferer");
        log.debug("2.8.1 verifyAccountRegistration");
        if (blockChainFacade.verifyAccountRegistration())
        {
            log.debug("2.8.2 isAccountBlackListed");
            if (!blockChainFacade.isAccountBlackListed(requestTradeMessage.getAccountID(), requestTradeMessage.getBankAccount()))
            {
                createAndSignContract(requestTradeMessage);
            }
            else
            {
                takerPaymentProtocolListener.onFailure("Offerer is blacklisted.");
            }
        }
        else
        {
            takerPaymentProtocolListener.onFailure("Offerers account registration is invalid.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.9  Create and sign the contract
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createAndSignContract(TradeMessage requestTradeMessage)
    {
        log.debug("2.9 createAndSignContract");
        checkNotNull(offer);
        checkNotNull(trade.getTradeAmount());
        checkNotNull(trade.getTakeOfferFeeTxID());
        checkNotNull(requestTradeMessage.getAccountID());
        checkNotNull(user.getAccountID());
        checkNotNull(requestTradeMessage.getBankAccount());
        checkNotNull(user.getCurrentBankAccount());
        checkNotNull(user.getMessagePubKeyAsHex());

        contract = new Contract(offer,
                trade.getTradeAmount(),
                trade.getTakeOfferFeeTxID(),
                requestTradeMessage.getAccountID(),
                user.getAccountID(),
                requestTradeMessage.getBankAccount(),
                user.getCurrentBankAccount(),
                offer.getMessagePubKeyAsHex(),
                user.getMessagePubKeyAsHex()
        );

        log.debug("2.9 contract created: " + contract.toString());
        String contractAsJson = Utilities.objectToJson(contract);
        String signature = cryptoFacade.signContract(walletFacade.getRegistrationKey(), contractAsJson);

        //log.debug("2.9 contractAsJson: " + contractAsJson);
        log.debug("2.9 contract signature: " + signature);

        trade.setContract(contract);
        trade.setContractAsJson(contractAsJson);
        trade.setContractTakerSignature(signature);

        payDeposit(requestTradeMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.10  Pay in the funds to the deposit tx and sign it
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void payDeposit(TradeMessage requestTradeMessage)
    {
        log.debug("2.10 payDeposit");

        BigInteger collateralAmount = trade.getCollateralAmount();
        BigInteger takerInputAmount = trade.getTradeAmount().add(collateralAmount);
        BigInteger msOutputAmount = trade.getTradeAmount().add(collateralAmount).add(collateralAmount);

        String offererPubKey = requestTradeMessage.getOffererPubKey();
        String takerPubKey = walletFacade.getPubKeyAsHex();
        String arbitratorPubKey = offer.getArbitrator().getPubKey();
        String preparedOffererDepositTxAsHex = requestTradeMessage.getPreparedOffererDepositTxAsHex();

        checkNotNull(takerInputAmount);
        checkNotNull(msOutputAmount);
        checkNotNull(offererPubKey);
        checkNotNull(takerPubKey);
        checkNotNull(arbitratorPubKey);
        checkNotNull(preparedOffererDepositTxAsHex);

        log.debug("2.10 offererCreatesMSTxAndAddPayment");
        log.debug("takerAmount     " + Utils.bitcoinValueToFriendlyString(takerInputAmount));
        log.debug("msOutputAmount     " + Utils.bitcoinValueToFriendlyString(msOutputAmount));
        log.debug("offerer pubkey    " + offererPubKey);
        log.debug("taker pubkey      " + takerPubKey);
        log.debug("arbitrator pubkey " + arbitratorPubKey);
        log.debug("preparedOffererDepositTxAsHex " + preparedOffererDepositTxAsHex);
        try
        {
            Transaction signedTakerDepositTx = walletFacade.takerAddPaymentAndSignTx(takerInputAmount, msOutputAmount, offererPubKey, takerPubKey, arbitratorPubKey, preparedOffererDepositTxAsHex);
            log.debug("2.10 deposit tx created: " + signedTakerDepositTx);
            sendSignedTakerDepositTxAsHex(signedTakerDepositTx);
        } catch (InterruptedException | AddressFormatException | ExecutionException | InsufficientMoneyException e)
        {
            log.error("2.10 error at walletFacade.takerAddPaymentAndSign: " + e.getMessage());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.11  Send the tx to the offerer
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void sendSignedTakerDepositTxAsHex(Transaction signedTakerDepositTx)
    {
        log.debug("2.11 sendSignedTakerDepositTxAsHex");

        TradeMessageListener listener = new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.debug("2.11 sendSignedTakerDepositTxAsHex REQUEST_TAKER_DEPOSIT_PAYMENT onResult");
                // Message arrived at taker
                takerPaymentProtocolListener.onProgress(getProgress());
            }

            @Override
            public void onFailed()
            {
                log.debug("2.11 sendSignedTakerDepositTxAsHex REQUEST_TAKER_DEPOSIT_PAYMENT onFailed");
                takerPaymentProtocolListener.onFailure("sendSignedTakerDepositTxAsHex REQUEST_TAKER_DEPOSIT_PAYMENT onFailed");
            }
        };

        takerPaymentProtocolListener.onProgress(getProgress());

        BankAccount bankAccount = user.getCurrentBankAccount();
        String accountID = user.getAccountID();
        String messagePubKey = user.getMessagePubKeyAsHex();
        String contractAsJson = trade.getContractAsJson();
        String signature = trade.getTakerSignature();

        String signedTakerDepositTxAsHex = com.google.bitcoin.core.Utils.bytesToHexString(signedTakerDepositTx.bitcoinSerialize());
        String txScriptSigAsHex = com.google.bitcoin.core.Utils.bytesToHexString(signedTakerDepositTx.getInput(1).getScriptBytes());
        String txConnOutAsHex = com.google.bitcoin.core.Utils.bytesToHexString(signedTakerDepositTx.getInput(1).getConnectedOutput().getParentTransaction().bitcoinSerialize());
        //TODO just 1 address supported yet
        String payoutAddress = walletFacade.getTradingAddress();
        log.debug("2.10 deposit txAsHex: " + signedTakerDepositTxAsHex);
        log.debug("2.10 txScriptSigAsHex: " + txScriptSigAsHex);
        log.debug("2.10 txConnOutAsHex: " + txConnOutAsHex);
        log.debug("2.10 payoutAddress: " + payoutAddress);

        TradeMessage tradeMessage = new TradeMessage(TradeMessageType.REQUEST_OFFERER_DEPOSIT_PUBLICATION,
                trade.getUid(),
                bankAccount,
                accountID,
                messagePubKey,
                signedTakerDepositTxAsHex,
                txScriptSigAsHex,
                txConnOutAsHex,
                contractAsJson,
                signature,
                payoutAddress);

        log.debug("2.11 sendTradingMessage");
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);
    }


    //************************************************************************************************
    // 3.1 - 3.5 Offerers tasks, we are in waiting mode
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.6  Incoming msg from offerer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onDepositTxPublished(TradeMessage tradeMessage)
    {
        log.debug("3.6 DepositTxID received: " + tradeMessage.getDepositTxAsHex());

        String txID = walletFacade.takerCommitDepositTx(tradeMessage.getDepositTxAsHex());
        takerPaymentProtocolListener.onProgress(getProgress());
        takerPaymentProtocolListener.onDepositTxPublished(txID);
    }


    //************************************************************************************************
    // 3.7-3.10 Offerers tasks, we are in waiting mode
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.11  Incoming msg from offerer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onBankTransferInited(TradeMessage tradeMessage)
    {
        log.debug("3.11 Bank transfer inited msg received");
        takerPaymentProtocolListener.onBankTransferInited(tradeMessage);
    }

    //************************************************************************************************
    // Taker will check periodically his bank account until he received the money. That might take a while...
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.12  User clicked the "bank transfer received" button, so we release the funds for pay out
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void releaseBTC(TradeMessage tradeMessage)
    {
        log.debug("3.12 releaseBTC");
        FutureCallback<Transaction> callback = new FutureCallback<Transaction>()
        {
            @Override
            public void onSuccess(Transaction transaction)
            {
                System.out.println("######### 3.12 onSuccess walletFacade.takerSignsAndSendsTx " + transaction.toString());
                log.debug("3.12 onSuccess walletFacade.takerSignsAndSendsTx " + transaction.toString());
                takerPaymentProtocolListener.onTradeCompleted(transaction.getHashAsString());

                sendPayoutTxToOfferer(Utils.bytesToHexString(transaction.bitcoinSerialize()));
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.error("######### 3.12 onFailure walletFacade.takerSignsAndSendsTx");
                System.err.println("3.12 onFailure walletFacade.takerSignsAndSendsTx");
                takerPaymentProtocolListener.onFailure("takerSignsAndSendsTx failed " + t.getMessage());
            }
        };
        try
        {
            String depositTxAsHex = tradeMessage.getDepositTxAsHex();
            String offererSignatureR = tradeMessage.getOffererSignatureR();
            String offererSignatureS = tradeMessage.getOffererSignatureS();
            BigInteger offererPaybackAmount = tradeMessage.getOffererPaybackAmount();
            BigInteger takerPaybackAmount = tradeMessage.getTakerPaybackAmount();
            String offererPayoutAddress = tradeMessage.getOffererPayoutAddress();

            log.debug("3.12  walletFacade.takerSignsAndSendsTx");
            walletFacade.takerSignsAndSendsTx(depositTxAsHex,
                    offererSignatureR,
                    offererSignatureS,
                    offererPaybackAmount,
                    takerPaybackAmount,
                    offererPayoutAddress,
                    callback);
        } catch (InsufficientMoneyException e)
        {
            log.error("3.12 offererCreatesAndSignsPayoutTx  onFailed InsufficientMoneyException " + e.getMessage());
        } catch (AddressFormatException e)
        {
            log.error("3.12 offererCreatesAndSignsPayoutTx  onFailed AddressFormatException " + e.getMessage());
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.13  Send payout txID to offerer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendPayoutTxToOfferer(String payoutTxAsHex)
    {
        log.debug("3.13 sendPayoutTxToOfferer ");
        TradeMessageListener listener = new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.debug("3.13 sendPayoutTxToOfferer PAYOUT_TX_PUBLISHED onResult");
                log.debug("3.13  TRADE COMPLETE!!!!!!!!!!!");
                takerPaymentProtocolListener.onProgress(getProgress());
            }

            @Override
            public void onFailed()
            {
                log.debug("3.13 sendPayoutTxToOfferer PAYOUT_TX_PUBLISHED onFailed");
                takerPaymentProtocolListener.onFailure("sendPayoutTxToOfferer PAYOUT_TX_PUBLISHED onFailed");
            }
        };

        TradeMessage tradeMessage = new TradeMessage(TradeMessageType.PAYOUT_TX_PUBLISHED, trade.getUid());
        tradeMessage.setPayoutTxAsHex(payoutTxAsHex);
        log.debug("3.13 sendTradeMessage PAYOUT_TX_PUBLISHED");
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);
    }

    private double getProgress()
    {
        currentStep++;
        return (double) currentStep / (double) numberOfSteps;
    }
}
