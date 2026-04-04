package home.ejaz.ledger.models;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Date;

public class Bucket implements Serializable {
  private Integer id;
  private String name;
  private BigDecimal budget;
  private Integer acctId;
  private BigDecimal refillFactor;
  private BigDecimal balance;

  public BigDecimal getPrevBalance() {
    return prevBalance;
  }

  public void setPrevBalance(BigDecimal prevBalance) {
    this.prevBalance = prevBalance;
  }

  private BigDecimal prevBalance;
  private BigDecimal refillMtd;

  public Bucket() {
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public BigDecimal getBudget() {
    return budget;
  }

  public void setBudget(BigDecimal budget) {
    this.budget = budget;
  }

  public Integer getAcctId() {
    return acctId;
  }

  public void setAcctId(Integer acctId) {
    this.acctId = acctId;
  }

  public BigDecimal getRefillFactor() {
    return refillFactor;
  }

  public void setRefillFactor(BigDecimal refillFactor) {
    this.refillFactor = refillFactor;
  }

  public BigDecimal getBalance() {
    return balance;
  }

  public void setBalance(BigDecimal balance) {
    this.balance = balance;
  }

  public BigDecimal getRefillMtd() {
    return refillMtd;
  }

  public void setRefillMtd(BigDecimal refillMtd) {
    this.refillMtd = refillMtd;
  }
}
