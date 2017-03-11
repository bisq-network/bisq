# Protobuffer migration

* classes sent over the wire (P2P network)
* classes serialized to disk

If possible we'll start with the P2P network because this has wider backward compatibility impact.

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

## P2P Network

### Extends Payload search results

```
public final class PubKeyRing implements Payload {
public final class SealedAndSigned implements Payload {
public final class PrivateNotification implements Payload {
public final class Dispute implements Payload {
public final class DisputeResult implements Payload {
public final class Attachment implements Payload {
public final class RawTransactionInput implements Payload {
public abstract class PaymentAccountContractData implements Payload {
public final class Contract implements Payload {
public final class NodeAddress implements Persistable, Payload {
public final class Peer implements Payload, Persistable {
public interface CapabilityRequiringPayload extends Payload {
public interface ExpirablePayload extends Payload {
public interface RequiresOwnerIsOnlinePayload extends Payload {
public class ProtectedStorageEntry implements Payload {
```

### Messages

```
public interface DirectMessage extends Message {
public interface SupportedCapabilitiesMessage extends Message {
public final class MockPayload implements Message, ExpirablePayload {
public interface AnonymousMessage extends Message {
public final class CloseConnectionMessage implements Message {
public interface SendersNodeAddressMessage extends Message {
public interface GetDataRequest extends Message {
public abstract class KeepAliveMessage implements Message {
abstract class PeerExchangeMessage implements Message {
public abstract class BroadcastMessage implements Message {

```

## Disk serialization

extends Serializable

```
private static class MockMessage implements Serializable {
public interface Persistable extends Serializable {
public class Tuple2<A, B> implements Serializable {
public class Tuple3<A, B, C> implements Serializable {
public class Tuple4<A, B, C, D> implements Serializable {
public static <T extends Serializable> T deserialize(byte[] data) {
public interface Payload extends Serializable {
public class PlainTextWrapper implements Serializable {
public class Storage<T extends Serializable> {
public abstract class HttpClientProvider implements Serializable {
public class PaymentAccountFilter implements Serializable {
public class CurrencyTuple implements Serializable {
public class ProcessModel implements Model, Serializable {
public final class Altcoin implements Monetary, Comparable<Altcoin>, Serializable {
public class AltcoinExchangeRate implements Serializable {
public interface Message extends Serializable {
public interface Message extends Serializable {
public static final class DataAndSeqNrPair implements Serializable {
                        
```                        


## Actually transformed subtypes of Message 

```
I Message (io.bitsquare.p2p)
I AnonymousMessage (io.bitsquare.p2p.network.messages)
+ PreliminaryGetDataRequest (io.bitsquare.p2p.peers.getdata.messages)
C BroadcastMessage (io.bitsquare.p2p.storage.messages)
+ AddDataMessage (io.bitsquare.p2p.storage.messages)
+ RefreshTTLMessage (io.bitsquare.p2p.storage.messages)
+ RemoveDataMessage (io.bitsquare.p2p.storage.messages)
+ RemoveMailboxDataMessage (io.bitsquare.p2p.storage.messages)
+ CloseConnectionMessage (io.bitsquare.p2p.network.messages)
I DirectMessage (io.bitsquare.p2p.messaging)
I MailboxMessage (io.bitsquare.p2p.messaging)
+ DepositTxPublishedMessage (io.bitsquare.trade.protocol.trade.messages)
C DisputeMessage (io.bitsquare.arbitration.messages)
+ DisputeCommunicationMessage (io.bitsquare.arbitration.messages)
+ DisputeResultMessage (io.bitsquare.arbitration.messages)
+ OpenNewDisputeMessage (io.bitsquare.arbitration.messages)
+ PeerOpenedDisputeMessage (io.bitsquare.arbitration.messages)
+ PeerPublishedPayoutTxMessage (io.bitsquare.arbitration.messages)
+ FiatTransferStartedMessage (io.bitsquare.trade.protocol.trade.messages)
+ FinalizePayoutTxRequest (io.bitsquare.trade.protocol.trade.messages)
+ MockMailboxPayload (io.bitsquare.p2p.mocks)
+ PayDepositRequest (io.bitsquare.trade.protocol.trade.messages)
+ PayoutTxFinalizedMessage (io.bitsquare.trade.protocol.trade.messages)
+ PrefixedSealedAndSignedMessage (io.bitsquare.p2p.messaging)
+ PrivateNotificationMessage (io.bitsquare.alert)
T StressTestMailboxMessage (io.bitsquare.p2p.network)
T TestMessage (io.bitsquare.crypto)
C OfferMessage (io.bitsquare.trade.protocol.availability.messages)
+ OfferAvailabilityRequest (io.bitsquare.trade.protocol.availability.messages)
+ OfferAvailabilityResponse (io.bitsquare.trade.protocol.availability.messages)
T StressTestDirectMessage (io.bitsquare.p2p.network)
C TradeMessage (io.bitsquare.trade.protocol.trade.messages)
+ DepositTxPublishedMessage (io.bitsquare.trade.protocol.trade.messages)
D FiatTransferStartedMessage (io.bitsquare.trade.protocol.trade.messages)
D FinalizePayoutTxRequest (io.bitsquare.trade.protocol.trade.messages)
D PayDepositRequest (io.bitsquare.trade.protocol.trade.messages)
D PayoutTxFinalizedMessage (io.bitsquare.trade.protocol.trade.messages)
+ PublishDepositTxRequest (io.bitsquare.trade.protocol.trade.messages)
I GetDataRequest (io.bitsquare.p2p.peers.getdata.messages)
+ GetUpdatedDataRequest (io.bitsquare.p2p.peers.getdata.messages)
D PreliminaryGetDataRequest (io.bitsquare.p2p.peers.getdata.messages)
C KeepAliveMessage (io.bitsquare.p2p.peers.keepalive.messages)
+ Ping (io.bitsquare.p2p.peers.keepalive.messages)
+ Pong (io.bitsquare.p2p.peers.keepalive.messages)
T MockPayload (io.bitsquare.p2p.mocks)
C PeerExchangeMessage (io.bitsquare.p2p.peers.peerexchange.messages)
+ GetPeersRequest (io.bitsquare.p2p.peers.peerexchange.messages)
+ GetPeersResponse (io.bitsquare.p2p.peers.peerexchange.messages)
I SendersNodeAddressMessage (io.bitsquare.p2p.network.messages)
D GetPeersRequest (io.bitsquare.p2p.peers.peerexchange.messages)
D GetUpdatedDataRequest (io.bitsquare.p2p.peers.getdata.messages)
D PrefixedSealedAndSignedMessage (io.bitsquare.p2p.messaging)
I SupportedCapabilitiesMessage (io.bitsquare.p2p.messaging)
+ GetDataResponse (io.bitsquare.p2p.peers.getdata.messages)
D GetPeersRequest (io.bitsquare.p2p.peers.peerexchange.messages)
D GetPeersResponse (io.bitsquare.p2p.peers.peerexchange.messages)
D OfferAvailabilityRequest (io.bitsquare.trade.protocol.availability.messages)
D OfferAvailabilityResponse (io.bitsquare.trade.protocol.availability.messages)
D PreliminaryGetDataRequest (io.bitsquare.p2p.peers.getdata.messages)
```

== Actually transformed subtypes of Payload

```
+ Attachment (io.bitsquare.arbitration.payload)
I CapabilityRequiringPayload (io.bitsquare.p2p.storage.payload)
+     TradeStatistics (io.bitsquare.trade.statistics)
+ Contract (io.bitsquare.trade)
+ Dispute (io.bitsquare.arbitration)
+ DisputeResult (io.bitsquare.arbitration)
I ExpirablePayload (io.bitsquare.p2p.storage.payload)
I     StoragePayload (io.bitsquare.p2p.storage.payload)
+        Alert (io.bitsquare.alert)
+        Arbitrator (io.bitsquare.arbitration)
+        Filter (io.bitsquare.filter)
I        LazyProcessedStoragePayload (io.bitsquare.p2p.storage.payload)
+            CompensationRequestPayload (io.bitsquare.dao.compensation)
D            TradeStatistics (io.bitsquare.trade.statistics)
+        MailboxStoragePayload (io.bitsquare.p2p.storage.payload)
+        Offer (io.bitsquare.trade.offer)
I        PersistedStoragePayload (io.bitsquare.p2p.storage.payload)
D            CompensationRequestPayload (io.bitsquare.dao.compensation)
D            TradeStatistics (io.bitsquare.trade.statistics)
+ NodeAddress (io.bitsquare.p2p)
C+PaymentAccountContractData (io.bitsquare.payment)
+    AliPayAccountContractData (io.bitsquare.payment)
+    ChaseQuickPayAccountContractData (io.bitsquare.payment)
+    ClearXchangeAccountContractData (io.bitsquare.payment)
C+   CountryBasedPaymentAccountContractData (io.bitsquare.payment)
C+       BankAccountContractData (io.bitsquare.payment)
+            NationalBankAccountContractData (io.bitsquare.payment)
+            SameBankAccountContractData (io.bitsquare.payment)
+            SpecificBanksAccountContractData (io.bitsquare.payment)
+        CashDepositAccountContractData (io.bitsquare.payment)
+        SepaAccountContractData (io.bitsquare.payment)
+    CryptoCurrencyAccountContractData (io.bitsquare.payment)
+    FasterPaymentsAccountContractData (io.bitsquare.payment)
+    InteracETransferAccountContractData (io.bitsquare.payment)
+    OKPayAccountContractData (io.bitsquare.payment)
+    PerfectMoneyAccountContractData (io.bitsquare.payment)
+    SwishAccountContractData (io.bitsquare.payment)
+    USPostalMoneyOrderAccountContractData (io.bitsquare.payment)
+ Peer (io.bitsquare.p2p.peers.peerexchange)
+ PrivateNotification (io.bitsquare.alert)
+ ProtectedStorageEntry (io.bitsquare.p2p.storage.storageentry)
+    ProtectedMailboxStorageEntry (io.bitsquare.p2p.storage.storageentry)
+ PubKeyRing (io.bitsquare.common.crypto)
+ RawTransactionInput (io.bitsquare.btc.data)
I RequiresOwnerIsOnlinePayload (io.bitsquare.p2p.storage.payload)
D    Offer (io.bitsquare.trade.offer)
+ SealedAndSigned (io.bitsquare.common.crypto)
```