package home.ejaz.ledger.models;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;

public class Transaction implements Serializable {
  private Long id;
  private Date txDate;
  private Integer bucketId;
  private String bucket;
  private BigDecimal amount;
  private BigDecimal balance;
  private String note;
  private boolean posted = false;

  public Transaction() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Date getTxDate() {
    return txDate;
  }

  public void setTxDate(Date txDate) {
    this.txDate = txDate;
  }

  public Integer getBucketId() {
    return bucketId;
  }

  public void setBucketId(Integer bucketId) {
    this.bucketId = bucketId;
  }

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal balance) {
    this.balance = balance;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public boolean isPosted() {
    return posted;
  }

  public void setPosted(boolean posted) {
    this.posted = posted;
  }

}
