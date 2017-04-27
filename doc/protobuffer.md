# Protobuffer FAQ

## Why protobuffer?

There are a number of reasons why protobuffer was chosen, here are some of them:
* avoids java serialisation security issues
* smaller in size than java serialisation (less network usage)
* All P2P network messages are described in a clear protobuffer schema
* allows to evolve your schema in a backward compatible way
* can generate code in many languages, making alternative bisq clients or monitoring tools easier

## Which classes are transformed to protobuffer?

All classes in the 'wire' module. This module contains the following classes:

* classes sent over the wire (P2P network) 
* classes serialized to disk

## Where are the protobuffer related files? 

The protobuffer schema file(s), generated classes and domain classes are in the 'wire' module.
Look for *.proto for the schema files.

## How is serialisation done (Java -> Protobuffer)

Some interfaces have a 'toProtobuf' method to force all extending classes to implement that method.

## How is deserialisation done (Java -> Protobuffer)

Some interfaces have a 'toProtobuf' method to force all extending classes to implement that method.


## If fields are not filled in, what are protobuffer's default values?

Read this very carefully:

https://developers.google.com/protocol-buffers/docs/proto3#default

## How to handle Enums 
 
For Java -> Protobuffer, you should extract the name from the Java enum:

    .setContext(PB.AddressEntry.Context.valueOf(context.name()))

For Protobuffer -> Java, do the opposite:

    OfferPayload.Direction.valueOf(direction.name());
  
# Other Stuff

## Frameworks

- Drop-in replacement for serialization, fixes size concerns but not backward compat:
https://ruedigermoeller.github.io/fast-serialization/
-

## Protobuffer Setup

### installing 

* Install the latest protobuffer release on your machine (3.2.0 at the time of writing):
https://github.com/google/protobuf/releases

* Increase the Intellij Idea Code insight limit, because it breaks on the generated protobuffer files:
Go to Help > Edit custom properties => paste the following line:
idea.max.intellisense.filesize=12500
Source: https://stackoverflow.com/questions/23057988/file-size-exceeds-configured-limit-2560000-code-insight-features-not-availabl

At IntelliJ 14 you need to edit the idea.properties in the app container:
/Applications/IntelliJ\ IDEA\ 14\ CE.app/Contents/bin/idea.properties 

### maven plugin vs ant plugin

* Bitcoinj uses an ant plugin to call the 'protoc' executable.
This was tried but didn't work, although command-line invocation with the same params worked.

* There is also a protobuf maven plugin
This worked immediately + is executed automatically when doing 'mvn clean install'.
Output is in target/generated-sources which avoids the temptation of checking in generated classes.

### multiple different messages in one stream

In order to support this, we need to use .writeDelimitedTo(outputstream) and parseDelimitedFrom(inputstream).
The writeDelimited writes a length varint before the object, allowing the parseDelimited to know the extent of the message. 
              

## Actually transformed subtypes of Message 

```
I Message (io.bisq.p2p)
I AnonymousMessage (io.bisq.p2p.network.messages)
+ PreliminaryGetDataRequest (io.bisq.p2p.peers.getdata.messages)
C BroadcastMessage (io.bisq.p2p.storage.messages)
+ AddDataMessage (io.bisq.p2p.storage.messages)
+ RefreshTTLMessage (io.bisq.p2p.storage.messages)
+ RemoveDataMessage (io.bisq.p2p.storage.messages)
+ RemoveMailboxDataMessage (io.bisq.p2p.storage.messages)
+ CloseConnectionMessage (io.bisq.p2p.network.messages)
I DirectMessage (io.bisq.p2p.messaging)
I MailboxMessage (io.bisq.p2p.messaging)
+ DepositTxPublishedMessage (io.bisq.trade.protocol.trade.messages)
C DisputeMessage (io.bisq.arbitration.messages)
+ DisputeCommunicationMessage (io.bisq.arbitration.messages)
+ DisputeResultMessage (io.bisq.arbitration.messages)
+ OpenNewDisputeMessage (io.bisq.arbitration.messages)
+ PeerOpenedDisputeMessage (io.bisq.arbitration.messages)
+ PeerPublishedPayoutTxMessage (io.bisq.arbitration.messages)
+ FiatTransferStartedMessage (io.bisq.trade.protocol.trade.messages)
+ FinalizePayoutTxRequest (io.bisq.trade.protocol.trade.messages)
+ MockMailboxPayload (io.bisq.p2p.mocks)
+ PayDepositRequest (io.bisq.trade.protocol.trade.messages)
+ PayoutTxFinalizedMessage (io.bisq.trade.protocol.trade.messages)
+ PrefixedSealedAndSignedMessage (io.bisq.p2p.messaging)
+ PrivateNotificationMessage (io.bisq.alert)
T StressTestMailboxMessage (io.bisq.p2p.network)
T TestMessage (io.bisq.crypto)
C OfferMessage (io.bisq.trade.protocol.availability.messages)
+ OfferAvailabilityRequest (io.bisq.trade.protocol.availability.messages)
+ OfferAvailabilityResponse (io.bisq.trade.protocol.availability.messages)
T StressTestDirectMessage (io.bisq.p2p.network)
C TradeMessage (io.bisq.trade.protocol.trade.messages)
+ DepositTxPublishedMessage (io.bisq.trade.protocol.trade.messages)
D FiatTransferStartedMessage (io.bisq.trade.protocol.trade.messages)
D FinalizePayoutTxRequest (io.bisq.trade.protocol.trade.messages)
D PayDepositRequest (io.bisq.trade.protocol.trade.messages)
D PayoutTxFinalizedMessage (io.bisq.trade.protocol.trade.messages)
+ PublishDepositTxRequest (io.bisq.trade.protocol.trade.messages)
I GetDataRequest (io.bisq.p2p.peers.getdata.messages)
+ GetUpdatedDataRequest (io.bisq.p2p.peers.getdata.messages)
D PreliminaryGetDataRequest (io.bisq.p2p.peers.getdata.messages)
C KeepAliveMessage (io.bisq.p2p.peers.keepalive.messages)
+ Ping (io.bisq.p2p.peers.keepalive.messages)
+ Pong (io.bisq.p2p.peers.keepalive.messages)
T MockPayload (io.bisq.p2p.mocks)
C PeerExchangeMessage (io.bisq.p2p.peers.peerexchange.messages)
+ GetPeersRequest (io.bisq.p2p.peers.peerexchange.messages)
+ GetPeersResponse (io.bisq.p2p.peers.peerexchange.messages)
I SendersNodeAddressMessage (io.bisq.p2p.network.messages)
D GetPeersRequest (io.bisq.p2p.peers.peerexchange.messages)
D GetUpdatedDataRequest (io.bisq.p2p.peers.getdata.messages)
D PrefixedSealedAndSignedMessage (io.bisq.p2p.messaging)
I SupportedCapabilitiesMessage (io.bisq.p2p.messaging)
+ GetDataResponse (io.bisq.p2p.peers.getdata.messages)
D GetPeersRequest (io.bisq.p2p.peers.peerexchange.messages)
D GetPeersResponse (io.bisq.p2p.peers.peerexchange.messages)
D OfferAvailabilityRequest (io.bisq.trade.protocol.availability.messages)
D OfferAvailabilityResponse (io.bisq.trade.protocol.availability.messages)
D PreliminaryGetDataRequest (io.bisq.p2p.peers.getdata.messages)
```

== Actually transformed subtypes of Payload

```
+ Attachment (io.bisq.arbitration.payload)
I CapabilityRequiringPayload (io.bisq.p2p.storage.payload)
+     TradeStatistics (io.bisq.trade.statistics)
+ Contract (io.bisq.trade)
+ Dispute (io.bisq.arbitration)
+ DisputeResult (io.bisq.arbitration)
I ExpirablePayload (io.bisq.p2p.storage.payload)
I     StoragePayload (io.bisq.p2p.storage.payload)
+        Alert (io.bisq.alert)
+        Arbitrator (io.bisq.arbitration)
+        Filter (io.bisq.filter)
I        LazyProcessedStoragePayload (io.bisq.p2p.storage.payload)
+            CompensationRequestPayload (io.bisq.dao.compensation)
D            TradeStatistics (io.bisq.trade.statistics)
+        MailboxStoragePayload (io.bisq.p2p.storage.payload)
+        Offer (io.bisq.trade.offerPayload)
I        PersistedStoragePayload (io.bisq.p2p.storage.payload)
D            CompensationRequestPayload (io.bisq.dao.compensation)
D            TradeStatistics (io.bisq.trade.statistics)
+ NodeAddress (io.bisq.p2p)
C+PaymentAccountContractData (io.bisq.payment)
+    AliPayAccountContractData (io.bisq.payment)
+    ChaseQuickPayAccountContractData (io.bisq.payment)
+    ClearXchangeAccountContractData (io.bisq.payment)
C+   CountryBasedPaymentAccountContractData (io.bisq.payment)
C+       BankAccountContractData (io.bisq.payment)
+            NationalBankAccountContractData (io.bisq.payment)
+            SameBankAccountContractData (io.bisq.payment)
+            SpecificBanksAccountContractData (io.bisq.payment)
+        CashDepositAccountContractData (io.bisq.payment)
+        SepaAccountContractData (io.bisq.payment)
+    CryptoCurrencyAccountContractData (io.bisq.payment)
+    FasterPaymentsAccountContractData (io.bisq.payment)
+    InteracETransferAccountContractData (io.bisq.payment)
+    OKPayAccountContractData (io.bisq.payment)
+    PerfectMoneyAccountContractData (io.bisq.payment)
+    SwishAccountContractData (io.bisq.payment)
+    USPostalMoneyOrderAccountContractData (io.bisq.payment)
+ Peer (io.bisq.p2p.peers.peerexchange)
+ PrivateNotification (io.bisq.alert)
+ ProtectedStorageEntry (io.bisq.p2p.storage.storageentry)
+    ProtectedMailboxStorageEntry (io.bisq.p2p.storage.storageentry)
+ PubKeyRing (io.bisq.common.crypto)
+ RawTransactionInput (io.bisq.btc.data)
I RequiresOwnerIsOnlinePayload (io.bisq.p2p.storage.payload)
D    Offer (io.bisq.trade.offerPayload)
+ SealedAndSigned (io.bisq.common.crypto)
```
