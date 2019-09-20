package monero.wallet.model;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import monero.utils.MoneroUtils;

/**
 * Models an outgoing transfer of funds from the wallet.
 */
public class MoneroOutgoingTransfer extends MoneroTransfer {

  private List<Integer> subaddressIndices;
  private List<String> addresses;
  private List<MoneroDestination> destinations;
  
  public MoneroOutgoingTransfer() {
    // nothing to initialize
  }
  
  public MoneroOutgoingTransfer(final MoneroOutgoingTransfer transfer) {
    super(transfer);
    if (transfer.subaddressIndices != null) this.subaddressIndices = new ArrayList<Integer>(transfer.subaddressIndices);
    if (transfer.addresses != null) this.addresses = new ArrayList<String>(transfer.addresses);
    if (transfer.destinations != null) {
      this.destinations = new ArrayList<MoneroDestination>();
      for (MoneroDestination destination : transfer.getDestinations()) {
        this.destinations.add(destination.copy()); 
      }
    }
  }
  
  @Override
  public MoneroOutgoingTransfer copy() {
    return new MoneroOutgoingTransfer(this);
  }
  
  @JsonProperty("isIncoming")
  public Boolean isIncoming() {
    return false;
  }
  
  public List<Integer> getSubaddressIndices() {
    return subaddressIndices;
  }

  public MoneroOutgoingTransfer setSubaddressIndices(List<Integer> subaddressIndices) {
    this.subaddressIndices = subaddressIndices;
    return this;
  }
  
  public List<String> getAddresses() {
    return addresses;
  }

  public MoneroOutgoingTransfer setAddresses(List<String> addresses) {
    this.addresses = addresses;
    return this;
  }

  public List<MoneroDestination> getDestinations() {
    return destinations;
  }
  
  public MoneroOutgoingTransfer setDestinations(List<MoneroDestination> destinations) {
    this.destinations = destinations;
    return this;
  }
  
  public MoneroOutgoingTransfer merge(MoneroTransfer transfer) {
    assertTrue(transfer instanceof MoneroOutgoingTransfer);
    return merge((MoneroOutgoingTransfer) transfer);
  }
  
  /**
   * Updates this transaction by merging the latest information from the given
   * transaction.
   * 
   * Merging can modify or build references to the transfer given so it
   * should not be re-used or it should be copied before calling this method.
   * 
   * @param transfer is the transfer to merge into this one
   * @return this transfer for chaining
   */
  public MoneroOutgoingTransfer merge(MoneroOutgoingTransfer transfer) {
    super.merge(transfer);
    assertTrue(transfer instanceof MoneroOutgoingTransfer);
    if (this == transfer) return this;
    this.setSubaddressIndices(MoneroUtils.reconcile(this.getSubaddressIndices(), transfer.getSubaddressIndices()));
    this.setAddresses(MoneroUtils.reconcile(this.getAddresses(), transfer.getAddresses()));
    this.setDestinations(MoneroUtils.reconcile(this.getDestinations(), transfer.getDestinations()));
    return this;
  }
  
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString(indent) + "\n");
    sb.append(MoneroUtils.kvLine("Subaddress indices", this.getSubaddressIndices(), indent));
    sb.append(MoneroUtils.kvLine("Addresses", this.getAddresses(), indent));
    if (this.getDestinations() != null) {
      sb.append(MoneroUtils.kvLine("Destinations", "", indent));
      for (int i = 0; i < this.getDestinations().size(); i++) {
        sb.append(MoneroUtils.kvLine(i + 1, "", indent + 1));
        sb.append(getDestinations().get(i).toString(indent + 2) + "\n");
      }
    }
    String str = sb.toString();
    return str.substring(0, str.length() - 1);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((addresses == null) ? 0 : addresses.hashCode());
    result = prime * result + ((destinations == null) ? 0 : destinations.hashCode());
    result = prime * result + ((subaddressIndices == null) ? 0 : subaddressIndices.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroOutgoingTransfer other = (MoneroOutgoingTransfer) obj;
    if (addresses == null) {
      if (other.addresses != null) return false;
    } else if (!addresses.equals(other.addresses)) return false;
    if (destinations == null) {
      if (other.destinations != null) return false;
    } else if (!destinations.equals(other.destinations)) return false;
    if (subaddressIndices == null) {
      if (other.subaddressIndices != null) return false;
    } else if (!subaddressIndices.equals(other.subaddressIndices)) return false;
    return true;
  }
  
  // ------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------

  @Override
  public MoneroOutgoingTransfer setTx(MoneroTxWallet tx) {
    super.setTx(tx);
    return this;
  }

  @Override
  public MoneroOutgoingTransfer setAmount(BigInteger amount) {
    super.setAmount(amount);
    return this;
  }

  @Override
  public MoneroOutgoingTransfer setAccountIndex(Integer accountIndex) {
    super.setAccountIndex(accountIndex);
    return this;
  }
  
  @Override
  public MoneroOutgoingTransfer setNumSuggestedConfirmations(Long numSuggestedConfirmations) {
    super.setNumSuggestedConfirmations(numSuggestedConfirmations);
    return this;
  }
}
