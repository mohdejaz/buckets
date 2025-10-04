package home.ejaz.ledger.models;

import java.io.Serializable;
import java.math.BigDecimal;

public class Bucket implements Serializable {
  public Integer id;
  public String name;
  public BigDecimal budget;
  public BigDecimal refillMtd;
  public BigDecimal balance;
  public Double refill;
  public Integer acctId;

  public Bucket() {
  }
}
