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

* install the latest protobuffer release on your machine (3.1.0 at this time of writing):
https://github.com/google/protobuf/releases

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
