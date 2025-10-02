package home.ejaz.ledger.models;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

public class Transaction implements Serializable {
  public Long id;
  public Date txDate;
  public String bucket;
  public BigDecimal amount;
  public BigDecimal balance;
  public String note;
  public boolean posted = false;

  public Transaction() {
  }
}
