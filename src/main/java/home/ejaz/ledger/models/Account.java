package home.ejaz.ledger.models;

import java.math.BigDecimal;

public class Account {
  private Integer id;
  private String name;
  private Integer userId;
  private BigDecimal balance;

  public Account() {}  // required by Spark

  public Integer getId() { return id; }
  public void setId(Integer id) { this.id = id; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public Integer getUserId() { return userId; }
  public void setUserId(Integer userId) { this.userId = userId; }

  public BigDecimal getBalance() { return balance; }
  public void setBalance(BigDecimal balance) { this.balance = balance; }
}
