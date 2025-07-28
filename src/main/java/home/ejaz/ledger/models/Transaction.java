package home.ejaz.ledger.models;

import java.math.BigDecimal;
import java.util.Date;

public class Transaction {
  public Long id;
  public Date txDate;
  public String bucket;
  public BigDecimal amount;
  public BigDecimal balance;
  public String note;
  public boolean posted = false;

  public Transaction(Long id, Date txDate, String bucket, BigDecimal amount, BigDecimal balance, String note) {
    this.id = id;
    this.txDate = txDate;
    this.bucket = bucket;
    this.amount = amount;
    this.note = note;
  }

  public Transaction() {
  }

  public String toString() {
    return new StringBuilder()
      .append("id=").append(id)
      .append(";date=").append(txDate)
      .append(";bucket=").append(bucket)
      .append(";amount=").append(amount)
      .append(";balance=").append(balance)
      .append(";note=").append(note).toString();

  }
}
