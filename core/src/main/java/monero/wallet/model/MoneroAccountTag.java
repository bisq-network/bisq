package monero.wallet.model;

import java.util.List;

/**
 * Represents an account tag.
 */
public class MoneroAccountTag {

  private String tag;
  private String label;
  private List<Integer> accountIndices;

  public MoneroAccountTag() {
    super();
  }

  public MoneroAccountTag(String tag, String label, List<Integer> accountIndices) {
    super();
    this.tag = tag;
    this.label = label;
    this.accountIndices = accountIndices;
  }

  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public List<Integer> getAccountIndices() {
    return accountIndices;
  }

  public void setAccountIndices(List<Integer> accountIndices) {
    this.accountIndices = accountIndices;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((accountIndices == null) ? 0 : accountIndices.hashCode());
    result = prime * result + ((label == null) ? 0 : label.hashCode());
    result = prime * result + ((tag == null) ? 0 : tag.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroAccountTag other = (MoneroAccountTag) obj;
    if (accountIndices == null) {
      if (other.accountIndices != null) return false;
    } else if (!accountIndices.equals(other.accountIndices)) return false;
    if (label == null) {
      if (other.label != null) return false;
    } else if (!label.equals(other.label)) return false;
    if (tag == null) {
      if (other.tag != null) return false;
    } else if (!tag.equals(other.tag)) return false;
    return true;
  }
}
