package home.ejaz.ledger.models;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

public class Bucket implements Serializable {
  public Integer id;
  public String name;
  public BigDecimal budget;
  public BigDecimal refillMtd;
  public BigDecimal balance;
  public String refillSchedule;
  public Date nextRefill;
  public double refillFactor;
  public Integer acctId;

  public Bucket() {
  }
}
