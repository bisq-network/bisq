package io.bitsquare.trade.payment.taker;

import com.google.bitcoin.core.Transaction;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.payment.offerer.messages.BankTransferInitedMessage;
import io.bitsquare.trade.payment.offerer.messages.DepositTxPublishedMessage;
import io.bitsquare.trade.payment.offerer.messages.RequestTakerDepositPaymentMessage;
import io.bitsquare.trade.payment.taker.tasks.*;
import io.bitsquare.user.User;
import io.nucleo.scheduler.SequenceScheduler;
import io.nucleo.scheduler.worker.Worker;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO use states
@SuppressWarnings("ConstantConditions")
public class TakerAsSellerProtocol
{
    private static final Logger log = LoggerFactory.getLogger(TakerAsSellerProtocol.class);

    // provided data
    private final String id;
    private final Trade trade;
    private final TakerAsSellerProtocolListener listener;
    private final WorkerResultHandler resultHandler;
    private final WorkerFaultHandler faultHandler;
    private final MessageFacade messageFacade;
    private final WalletFacade walletFacade;
    private final BlockChainFacade blockChainFacade;
    private final CryptoFacade cryptoFacade;
    private final User user;
    // private
    private final SequenceScheduler scheduler_1;
    // written/read by task
    private String payoutTxAsHex;
    private PeerAddress peerAddress;
    private Transaction signedTakerDepositTx;
    private long takerTxOutIndex;
    // written by message, read by tasks
    private String peersAccountId;
    private BankAccount peersBankAccount;
    private String offererPubKey;
    private String preparedOffererDepositTxAsHex;
    private long offererTxOutIndex;
    private String depositTxAsHex;
    private String offererSignatureR;
    private String offererSignatureS;
    private BigInteger offererPaybackAmount;
    private BigInteger takerPaybackAmount;
    private String offererPayoutAddress;
    private int currentStep = 0;
    private SequenceScheduler scheduler_2;
    private SequenceScheduler scheduler_3;
    private SequenceScheduler scheduler_4;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TakerAsSellerProtocol(Trade trade,
                                 TakerAsSellerProtocolListener listener,
                                 WorkerResultHandler resultHandler,
                                 WorkerFaultHandler faultHandler,
                                 MessageFacade messageFacade,
                                 WalletFacade walletFacade,
                                 BlockChainFacade blockChainFacade,
                                 CryptoFacade cryptoFacade,
                                 User user)
    {
        this.trade = trade;
        this.listener = listener;
        this.resultHandler = resultHandler;
        this.faultHandler = faultHandler;
        this.messageFacade = messageFacade;
        this.walletFacade = walletFacade;
        this.blockChainFacade = blockChainFacade;
        this.cryptoFacade = cryptoFacade;
        this.user = user;

        id = trade.getId();

        log.debug("TakerAsSellerProtocol created");

        messageFacade.addTakerPaymentProtocol(this);

        // start with first task sequence
        List<Worker> tasks = new ArrayList<>();
        tasks.add(new GetPeerAddress(resultHandler, faultHandler));
        tasks.add(new RequestTakeOffer(resultHandler, faultHandler));
        scheduler_1 = new SequenceScheduler(tasks, this);
        scheduler_1.execute();
    }

    public void onAcceptTakeOfferRequestMessage()
    {
        log.debug("onAcceptTakeOfferRequestMessage");
        if (scheduler_1.getHasCompleted())
        {
            List<Worker> tasks = new ArrayList<>();
            tasks.add(new PayTakeOfferFee(resultHandler, faultHandler));
            tasks.add(new SendTakeOfferFeePayedTxId(resultHandler, faultHandler));
            scheduler_2 = new SequenceScheduler(tasks, this);
            scheduler_2.execute();
        }
        else
        {
            log.error("scheduler_1 has not completed yet.");
        }
    }

    public void onRejectTakeOfferRequestMessage()
    {
        log.debug("onRejectTakeOfferRequestMessage");
        if (scheduler_1.getHasCompleted())
        {
            //TODO
        }
        else
        {
            log.error("scheduler_1 has not completed yet.");
        }
    }

    public void onRequestTakerDepositPaymentMessage(RequestTakerDepositPaymentMessage message)
    {
        log.debug("onRequestTakerDepositPaymentMessage");
        peersAccountId = message.getAccountID();
        peersBankAccount = message.getBankAccount();
        offererPubKey = message.getOffererPubKey();
        preparedOffererDepositTxAsHex = message.getPreparedOffererDepositTxAsHex();
        offererTxOutIndex = message.getOffererTxOutIndex();

        if (scheduler_2.getHasCompleted())
        {
            List<Worker> tasks = new ArrayList<>();
            tasks.add(new VerifyOffererAccount(resultHandler, faultHandler));
            tasks.add(new CreateAndSignContract(resultHandler, faultHandler));
            tasks.add(new PayDeposit(resultHandler, faultHandler));
            tasks.add(new SendSignedTakerDepositTxAsHex(resultHandler, faultHandler));
            scheduler_3 = new SequenceScheduler(tasks, this);
            scheduler_3.execute();
        }
        else
        {
            log.error("scheduler_2 has not completed yet.");
        }
    }

    // informational, does only trigger UI feedback/update
    public void onDepositTxPublishedMessage(DepositTxPublishedMessage message)
    {
        log.debug("onDepositTxPublishedMessage");
        String txID = getWalletFacade().takerCommitDepositTx(message.getDepositTxAsHex());
        listener.onDepositTxPublished(txID);
    }

    // informational, store data for later, does only trigger UI feedback/update
    public void onBankTransferInitedMessage(BankTransferInitedMessage tradeMessage)
    {
        log.debug("onBankTransferInitedMessage");

        depositTxAsHex = tradeMessage.getDepositTxAsHex();
        offererSignatureR = tradeMessage.getOffererSignatureR();
        offererSignatureS = tradeMessage.getOffererSignatureS();
        offererPaybackAmount = tradeMessage.getOffererPaybackAmount();
        takerPaybackAmount = tradeMessage.getTakerPaybackAmount();
        offererPayoutAddress = tradeMessage.getOffererPayoutAddress();

        listener.onBankTransferInited(tradeMessage.getTradeId());
    }

    // User clicked the "bank transfer received" button, so we release the funds for pay out
    public void onUIEventFiatReceived()
    {
        log.debug("onUIEventFiatReceived");
        if (scheduler_3.getHasCompleted())
        {
            List<Worker> tasks = new ArrayList<>();
            tasks.add(new SignAndPublishPayoutTx(resultHandler, faultHandler));
            tasks.add(new SendPayoutTxToOfferer(resultHandler, faultHandler));
            scheduler_4 = new SequenceScheduler(tasks, this);
            scheduler_4.execute();
        }
        else
        {
            log.error("scheduler_3 has not completed yet.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters, Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getId()
    {
        return id;
    }

    public Trade getTrade()
    {
        return trade;
    }

    public TakerAsSellerProtocolListener getListener()
    {
        return listener;
    }

    public MessageFacade getMessageFacade()
    {
        return messageFacade;
    }

    public WalletFacade getWalletFacade()
    {
        return walletFacade;
    }

    public BlockChainFacade getBlockChainFacade()
    {
        return blockChainFacade;
    }

    public CryptoFacade getCryptoFacade()
    {
        return cryptoFacade;
    }

    public User getUser()
    {
        return user;
    }

    public PeerAddress getPeerAddress()
    {
        return peerAddress;
    }

    public void setPeerAddress(PeerAddress peerAddress)
    {
        this.peerAddress = peerAddress;
    }

    public Transaction getSignedTakerDepositTx()
    {
        return signedTakerDepositTx;
    }

    public void setSignedTakerDepositTx(Transaction signedTakerDepositTx)
    {
        this.signedTakerDepositTx = signedTakerDepositTx;
    }

    public long getTakerTxOutIndex()
    {
        return takerTxOutIndex;
    }

    public void setTakerTxOutIndex(long takerTxOutIndex)
    {
        this.takerTxOutIndex = takerTxOutIndex;
    }

    public String getPeersAccountId()
    {
        return peersAccountId;
    }

    public BankAccount getPeersBankAccount()
    {
        return peersBankAccount;
    }

    public String getOffererPubKey()
    {
        return offererPubKey;
    }

    public String getPreparedOffererDepositTxAsHex()
    {
        return preparedOffererDepositTxAsHex;
    }

    public long getOffererTxOutIndex()
    {
        return offererTxOutIndex;
    }

    public String getDepositTxAsHex()
    {
        return depositTxAsHex;
    }

    public String getOffererSignatureR()
    {
        return offererSignatureR;
    }

    public String getOffererSignatureS()
    {
        return offererSignatureS;
    }

    public BigInteger getOffererPaybackAmount()
    {
        return offererPaybackAmount;
    }

    public BigInteger getTakerPaybackAmount()
    {
        return takerPaybackAmount;
    }

    public String getOffererPayoutAddress()
    {
        return offererPayoutAddress;
    }

    public String getPayoutTxAsHex()
    {
        return payoutTxAsHex;
    }

    public void setPayoutTxAsHex(String payoutTxAsHex)
    {
        this.payoutTxAsHex = payoutTxAsHex;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 1.1
    ///////////////////////////////////////////////////////////////////////////////////////////


   /* private void findPeerAddress()
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
    }  */

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 1.2
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* private void requestTakeOffer()
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
        TradeMessage tradeMessage = new TradeMessage(TradeMessageType.REQUEST_TAKE_OFFER, trade.getId());
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);
    }   */


    //************************************************************************************************
    // 1.3. Offerers tasks, we are in waiting mode
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 1.4
    ///////////////////////////////////////////////////////////////////////////////////////////


    // 1.4a offerer has accepted the take offer request. Move on to step 2.
   /* public void onAcceptTakeOfferRequestMessage()
    {
        log.debug("1.4a onAcceptTakeOfferRequestMessage");
        // listener.onProgress(getProgress());

        payOfferFee(trade);
    }

    // 1.4b Offerer has rejected the take offer request. The UI controller will onResult the case.
    public void onRejectTakeOfferRequestMessage()
    {
        log.debug("1.4b onRejectTakeOfferRequestMessage");
        // listener.onProgress(getProgress());
    }  */


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.1
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* private void payOfferFee(Trade trade)
    {
        log.debug("2.1 payTakeOfferFee");
        FutureCallback<Transaction> callback = new FutureCallback<Transaction>()
        {
            @Override
            public void onSuccess(@javax.annotation.Nullable Transaction transaction)
            {
                log.debug("2.1 payTakeOfferFee onSuccess");
                log.info("sendResult onSuccess txid:" + transaction.getHashAsString());

                // Offer fee payed successfully.
                trade.setTakeOfferFeeTxID(transaction.getHashAsString());

                // listener.onProgress(getProgress());

                // move on
                sendTakerOfferFeeTxID(transaction.getHashAsString());
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.debug("2.1 payTakeOfferFee onFailure");
                // listener.onFailure("payTakeOfferFee onFailure " + t.getMessage());
            }
        };
        try
        {
            // Pay the offer fee
            // listener.onProgress(getProgress());
            walletFacade.payTakeOfferFee(trade.getId(), callback);
        } catch (InsufficientMoneyException e)
        {
            // listener.onProgress(getProgress());
        }
    }   */


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.2
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Request peers account details. At that moment private data (bank account nr.) of the offerer get visible to the taker.
    // We send also the tx id of the fee payment, so the offerer can confirm that the payment is seen in the network
    // 0 block confirmation is acceptable for the fee to not block the process
    // The offerer will wait until a minimum of peers seen the tx before sending his data.
    // We also get the multisig tx delivered
   /* private void sendTakerOfferFeeTxID(String takeOfferFeeTxID)
    {
        log.debug("2.2 sendTakerOfferFeeTxID");
        TradeMessageListener listener = new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.debug("2.2 sendTakerOfferFeeTxID onResult");
                // Message has arrived
                //listener.onProgress(getProgress());

                // We wait until the offerer send us the data
            }

            @Override
            public void onFailed()
            {
                log.debug("2.2 sendTakerOfferFeeTxID onFailed");
                // //TakerAsSellerProtocol.this. listener.onFailure("requestAccountDetails onSendTradingMessageFailed");
            }
        };

        // this.listener.onProgress(getProgress());

        // 2.3. send request for the account details and send fee tx id so offerer can verify that the fee has been paid.
        TradeMessageOld tradeMessage = new TradeMessageOld(TradeMessageType.TAKE_OFFER_FEE_PAYED,
                trade.getId(),
                trade.getTradeAmount(),
                takeOfferFeeTxID,
                walletFacade.getAddressInfoByTradeID(trade.getId()).getPubKeyAsHexString());
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);
    }
       */

    //************************************************************************************************
    // 2.3 - 2.6 Offerers tasks, we are in waiting mode
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.7  Incoming msg from offerer
    ///////////////////////////////////////////////////////////////////////////////////////////

    /*public void onRequestTakerDepositPaymentMessage(TradeMessageOld requestTradeMessage)
    {
        log.debug("2.7 onRequestTakerDepositPaymentMessage");
        verifyOfferer(requestTradeMessage);
    } */


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.8  Verify offerers account registration and against the blacklist
    ///////////////////////////////////////////////////////////////////////////////////////////

    /*private void verifyOfferer(TradeMessageOld requestTradeMessage)
    {
        log.debug("2.8 verifyOfferer");
        log.debug("2.8.1 verifyAccountRegistration");
        if (blockChainFacade.verifyAccountRegistration())
        {
            log.debug("2.8.2 isAccountBlackListed");
            if (blockChainFacade.isAccountBlackListed(requestTradeMessage.getAccountId(), requestTradeMessage.getBankAccount()))
            {
                // listener.onFailure("Offerer is blacklisted.");
            }
            else
            {
                createAndSignContract(requestTradeMessage);
            }
        }
        else
        {
            // listener.onFailure("Offerers account registration is invalid.");
        }
    }  */


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.9  Create and sign the contract
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* private void createAndSignContract(TradeMessageOld requestTradeMessage)
    {
        log.debug("2.9 createAndSignContract");
        checkNotNull(trade.getOffer());
        checkNotNull(trade.getTradeAmount());
        checkNotNull(trade.getTakeOfferFeeTxId());
        checkNotNull(requestTradeMessage.getAccountId());
        checkNotNull(user.getAccountId());
        checkNotNull(requestTradeMessage.getBankAccount());
        checkNotNull(user.getCurrentBankAccount());
        checkNotNull(user.getMessagePubKeyAsHex());

        Contract contract = new Contract(trade.getOffer(),
                trade.getTradeAmount(),
                trade.getTakeOfferFeeTxId(),
                requestTradeMessage.getAccountId(),
                user.getAccountId(),
                requestTradeMessage.getBankAccount(),
                user.getCurrentBankAccount(),
                trade.getOffer().getMessagePubKeyAsHex(),
                user.getMessagePubKeyAsHex()
        );

        log.debug("2.9 contract created: " + contract);
        String contractAsJson = Utilities.objectToJson(contract);
        String signature = cryptoFacade.signContract(walletFacade.getRegistrationAddressInfo().getKey(), contractAsJson);

        //log.debug("2.9 contractAsJson: " + contractAsJson);
        log.debug("2.9 contract signature: " + signature);

        trade.setContract(contract);
        trade.setContractAsJson(contractAsJson);
        trade.setContractTakerSignature(signature);

        payDeposit(requestTradeMessage);
    }   */


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.10  Pay in the funds to the deposit tx and sign it
    ///////////////////////////////////////////////////////////////////////////////////////////

    /*private void payDeposit(TradeMessageOld requestTradeMessage)
    {
        log.debug("2.10 payDeposit");

        BigInteger collateralAmount = trade.getCollateralAmount();
        BigInteger takerInputAmount = trade.getTradeAmount().add(collateralAmount);
        BigInteger msOutputAmount = trade.getTradeAmount().add(collateralAmount).add(collateralAmount);

        String offererPubKey = requestTradeMessage.getOffererPubKey();
        String takerPubKey = walletFacade.getAddressInfoByTradeID(trade.getId()).getPubKeyAsHexString();
        String arbitratorPubKey = trade.getOffer().getArbitrator().getPubKeyAsHex();
        String preparedOffererDepositTxAsHex = requestTradeMessage.getPreparedOffererDepositTxAsHex();

        checkNotNull(takerInputAmount);
        checkNotNull(msOutputAmount);
        checkNotNull(offererPubKey);
        checkNotNull(takerPubKey);
        checkNotNull(arbitratorPubKey);
        checkNotNull(preparedOffererDepositTxAsHex);

        log.debug("2.10 offererCreatesMSTxAndAddPayment");
        log.debug("takerAmount     " + BtcFormatter.formatSatoshis(takerInputAmount));
        log.debug("msOutputAmount     " + BtcFormatter.formatSatoshis(msOutputAmount));
        log.debug("offerer pubkey    " + offererPubKey);
        log.debug("taker pubkey      " + takerPubKey);
        log.debug("arbitrator pubkey " + arbitratorPubKey);
        log.debug("preparedOffererDepositTxAsHex " + preparedOffererDepositTxAsHex);
        try
        {
            Transaction signedTakerDepositTx = walletFacade.takerAddPaymentAndSignTx(takerInputAmount, msOutputAmount, offererPubKey, takerPubKey, arbitratorPubKey, preparedOffererDepositTxAsHex, trade.getId());
            log.debug("2.10 deposit tx created: " + signedTakerDepositTx);
            long takerTxOutIndex = signedTakerDepositTx.getInput(1).getOutpoint().getIndex();
            sendSignedTakerDepositTxAsHex(signedTakerDepositTx, takerTxOutIndex, requestTradeMessage.getOffererTxOutIndex());
        } catch (InsufficientMoneyException e)
        {
            log.error("2.10 error at walletFacade.takerAddPaymentAndSign: " + e.getMessage());
        }
    }   */


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 2.11  Send the tx to the offerer
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* private void sendSignedTakerDepositTxAsHex(Transaction signedTakerDepositTx, long takerTxOutIndex, long offererTxOutIndex)
    {
        log.debug("2.11 sendSignedTakerDepositTxAsHex");

        TradeMessageListener listener = new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.debug("2.11 sendSignedTakerDepositTxAsHex REQUEST_TAKER_DEPOSIT_PAYMENT onResult");
                // Message arrived at taker
                // //TakerAsSellerProtocol.this. listener.onFailure(getProgress());
            }

            @Override
            public void onFailed()
            {
                log.debug("2.11 sendSignedTakerDepositTxAsHex REQUEST_TAKER_DEPOSIT_PAYMENT onFailed");
                ////TakerAsSellerProtocol.this. listener.onFailure("sendSignedTakerDepositTxAsHex REQUEST_TAKER_DEPOSIT_PAYMENT onFailed");
            }
        };

        // this.listener.onProgress(getProgress());

        BankAccount bankAccount = user.getCurrentBankAccount();
        String accountID = user.getAccountId();
        String messagePubKey = user.getMessagePubKeyAsHex();
        String contractAsJson = trade.getContractAsJson();
        String signature = trade.getTakerSignature();

        String signedTakerDepositTxAsHex = com.google.bitcoin.core.Utils.bytesToHexString(signedTakerDepositTx.bitcoinSerialize());
        String txScriptSigAsHex = com.google.bitcoin.core.Utils.bytesToHexString(signedTakerDepositTx.getInput(1).getScriptBytes());
        String txConnOutAsHex = com.google.bitcoin.core.Utils.bytesToHexString(signedTakerDepositTx.getInput(1).getConnectedOutput().getParentTransaction().bitcoinSerialize());
        //TODO just 1 address supported yet
        String payoutAddress = walletFacade.getAddressInfoByTradeID(trade.getId()).getAddressString();
        log.debug("2.10 deposit txAsHex: " + signedTakerDepositTxAsHex);
        log.debug("2.10 txScriptSigAsHex: " + txScriptSigAsHex);
        log.debug("2.10 txConnOutAsHex: " + txConnOutAsHex);
        log.debug("2.10 payoutAddress: " + payoutAddress);

        TradeMessageOld tradeMessage = new TradeMessageOld(TradeMessageType.REQUEST_OFFERER_DEPOSIT_PUBLICATION,
                trade.getId(),
                bankAccount,
                accountID,
                messagePubKey,
                signedTakerDepositTxAsHex,
                txScriptSigAsHex,
                txConnOutAsHex,
                contractAsJson,
                signature,
                payoutAddress,
                takerTxOutIndex,
                offererTxOutIndex
        );

        log.debug("2.11 sendTradingMessage");
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);
    }  */


    //************************************************************************************************
    // 3.1 - 3.5 Offerers tasks, we are in waiting mode
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.6  Incoming msg from offerer
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* public void onDepositTxPublishedMessage(TradeMessageOld tradeMessage)
    {
        log.debug("3.6 DepositTxID received: " + tradeMessage.getDepositTxAsHex());

        String txID = walletFacade.takerCommitDepositTx(tradeMessage.getDepositTxAsHex());
        // listener.onProgress(getProgress());
        listener.onDepositTxPublishedMessage(txID);
    } */


    //************************************************************************************************
    // 3.7-3.10 Offerers tasks, we are in waiting mode
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.11  Incoming msg from offerer
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* public void onBankTransferInitedMessage(BankTransferInitedMessage tradeMessage)
    {
        log.debug("3.11 Bank transfer inited msg received");
        listener.onBankTransferInitedMessage(tradeMessage);
    }  */

    //************************************************************************************************
    // Taker will check periodically his bank account until he received the money. That might take a while...
    //************************************************************************************************


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.12  User clicked the "bank transfer received" button, so we release the funds for pay out
    ///////////////////////////////////////////////////////////////////////////////////////////

   /* public void onUIEventFiatReceived(TradeMessageOld tradeMessage)
    {
        log.debug("3.12 onUIEventFiatReceived");
        FutureCallback<Transaction> callback = new FutureCallback<Transaction>()
        {
            @Override
            public void onSuccess(@javax.annotation.Nullable Transaction transaction)
            {
                System.out.println("######### 3.12 onSuccess walletFacade.takerSignsAndSendsTx " + transaction);
                log.debug("3.12 onSuccess walletFacade.takerSignsAndSendsTx " + transaction);
                listener.onTradeCompleted(transaction.getHashAsString());

                sendPayoutTxToOfferer(Utils.bytesToHexString(transaction.bitcoinSerialize()));
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.error("######### 3.12 onFailure walletFacade.takerSignsAndSendsTx");
                System.err.println("3.12 onFailure walletFacade.takerSignsAndSendsTx");
                // listener.onFailure("takerSignsAndSendsTx failed " + t.getMessage());
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
                    trade.getId(),
                    callback);
        } catch (AddressFormatException e)
        {
            log.error("3.12 offererCreatesAndSignsPayoutTx  onFailed AddressFormatException " + e.getMessage());
        }
    }   */

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Step 3.13  Send payout txID to offerer
    ///////////////////////////////////////////////////////////////////////////////////////////

    /*void sendPayoutTxToOfferer(String payoutTxAsHex)
    {
        log.debug("3.13 sendPayoutTxToOfferer ");
        TradeMessageListener listener = new TradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.debug("3.13 sendPayoutTxToOfferer PAYOUT_TX_PUBLISHED onResult");
                log.debug("3.13  TRADE COMPLETE!!!!!!!!!!!");
                //TakerAsSellerProtocol.this. listener.onFailure(getProgress());
            }

            @Override
            public void onFailed()
            {
                log.debug("3.13 sendPayoutTxToOfferer PAYOUT_TX_PUBLISHED onFailed");
                //TakerAsSellerProtocol.this. listener.onFailure("sendPayoutTxToOfferer PAYOUT_TX_PUBLISHED onFailed");
            }
        };

        TradeMessageOld tradeMessage = new TradeMessageOld(TradeMessageType.PAYOUT_TX_PUBLISHED, trade.getId());
        tradeMessage.setPayoutTxAsHex(payoutTxAsHex);
        log.debug("3.13 sendTradeMessage PAYOUT_TX_PUBLISHED");
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, listener);
    }  */

   /* private double getProgress()
    {
        currentStep++;
        int numberOfSteps = 10;
        return (double) currentStep / (double) numberOfSteps;
    } */

}
