package home.ejaz.ledger.models;

import java.math.BigDecimal;

public class Bucket {
  public Integer id;
  public String name;
  public BigDecimal budget;
  public BigDecimal refillMtd;
  public BigDecimal balance;

  public Bucket(Integer id, String name, BigDecimal budget, BigDecimal refillMtd, BigDecimal balance) {
    this.id = id;
    this.name = name;
    this.budget = budget;
    this.refillMtd = refillMtd;
    this.balance = balance;
  }

  public Bucket() {
  }
}
