# Protobuffer FAQ

NOTE: this doc is out of date and should probably be deleted. Protobuffer stuff is all managed in bisq-core now, not bisq-desktop.

## Installation

Protobuffer is installed automatically via the Gradle build.

## Why Protobuffer?

There are a number of reasons why protobuffer was chosen, here are some of them:
* avoids java serialisation security issues
* smaller in size than java serialisation (less network usage)
* All P2P network messages are described in a clear Protobuffer schema
* allows to evolve your schema in a backward compatible way
* can generate code in many languages, making alternative bisq clients or monitoring tools easier

## Which classes are transformed to Protobuffer?

All classes in the 'wire' module. This module contains the following classes:

* classes sent over the wire (P2P network)
* classes serialized to disk

## Where are the Protobuffer related files?

The Protobuffer schema file(s), generated classes and domain classes are in the 'wire' module.
Look for *.proto for the schema files.

## How is serialisation done (Java -> Protobuffer)

Some interfaces have a 'toProtobuf' method to force all extending classes to implement that method.

## How is deserialisation done (Java -> Protobuffer)

Some interfaces have a 'toProtobuf' method to force all extending classes to implement that method.


## If fields are not filled in, what are Protobuffer's default values?

Read this very carefully:

https://developers.google.com/protocol-buffers/docs/proto3#default

## How to handle Enums

For Java -> Protobuffer, you should extract the name from the Java enum:

    .setContext(PB.AddressEntry.Context.valueOf(context.name()))

For Protobuffer -> Java, use the ProtoUtil helper method 'enumFromProto' to avoid crashes:

    ProtoUtil.enumFromProto(OfferPayload.Direction.class, direction.name());

## How to handle Date

For Java -> Protobuffer, you should extract the name from the Java enum:

    .setDate(date.getTime())

For Protobuffer -> Java, do the opposite:

    Date date = new Date(PB.bla.getDate());

# Other Stuff

## Frameworks

- Drop-in replacement for serialization, fixes size concerns but not backward compat:
https://ruedigermoeller.github.io/fast-serialization/
-

## Checklist and conventions

### Code style
* Use line breaks after each param in constructor and PB methods
* Use line breaks at PB builders for best readability
* Use after the constructor a comment separator with the PB stuff and after the PB stuff a comment separator with API
* Use same order of fields as in main constructor and follow that order in the PB methods to make it easier to spot missing fields

### Conventions
* Try to use value objects for data which gets serialized with PB
* Use toProto and a static fromProto in the class which gets serialized
* Use proto as name for the PB param in the fromProto method
* If a constructor is used only for PB make it private and put it to the PB section, so it's clear it is used only in that context
* Use same name for classes and fields in PB definition as in the java code base
* Use final
* Use Lombok Annotations
* Annotate all nullable fields with @Nullable
* If nullable fields must not be null at time of serialisation use checkNotNull with comment inside
* Use ProtoUtil convenience methods (e.g. stringOrNullFromProto, byteArrayOrNullFromProto, collectionToProto, enumFromProto,..)
* Use ProtoUtil.enumFromProto for all enums
* Enum in PB definition file needs an additional first entry with PB_ERROR in case of multiple enums in one message add postfix of enum (PB_ERROR_REASON = 0;)
* When serializing a custom type use the toProto and fromProto methods in the class of that type.
* Use the ProtoResolver classes for switching between different message cases. Pass the reference to the resolver if needed in fromProto
* For abstract super classes use a getBuilder method for handling the fields in that super class (e.g. PaymentAccountPayload)
* Use Payload as postfix for objects which are used in Envelopes or other Payloads if it helps to distinguish between the domain object and the value object (e.g. UserPayload)
* Separate network and persistable domains if possible

### Architecture
* Envelope is the base interface for all objects carrying PB data (messages, persisted objects)
* Payload is used inside Envelopes or other Payloads
* Interface structure:
    Proto: base has  Message toProtoMessage();
        Envelope extends Proto: Marker interface for objects carrying PB data
            NetworkEnvelope extends Envelope: Marker interface, has getDefaultBuilder for P2PMessageVersion
            PersistableEnvelope extends Envelope: Marker interface
        Payload extends Proto: Marker interface for objects used inside other Payload or Envelope objects
            NetworkPayload extends Payload: Marker interface
            PersistablePayload extends Payload: Marker interface
    ProtoResolver: Base for resolver (switch message cases)
        NetworkProtoResolver extends ProtoResolver: Marker interface
            CoreNetworkProtoResolver implements NetworkProtoResolver: Implements switches for network messages
        PersistableProtoResolver extends ProtoResolver: Marker interface
            CorePersistableProtoResolver implements NetworkProtoResolver: Implements switches for persistable messages



### Check list
* Treat nullable fields correctly in the toProto and fromProto methods.
    PB does not support null values but use default implementation for not set fields.
    Depending on the type there is: isEmpty (string, collections) or has[Propertyname]
* If using @EqualsAndHashCode or @Data/@Value make sure to use callSuper=true if the class is extending another class
* If collections are modifiable take care to wrap the result of PB to a modifiable collection. PB delivers unmodifiable collections
* For network envelopes use NetworkEnvelope.getDefaultBuilder() which includes the P2PMessageVersion.
    Store the messageVersion in all NetworkEnvelope instances



// TODO update, outdated...
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
+    WeChatPayAccountContractData (io.bisq.payment)
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
