package home.ejaz.ledger;

public interface LedgerListener {

  void txAdded(long id);

  void txUpdate(long id);

  void txDelete(long id);

  void bkAdded(int id);

  void bkUpdate(int id);

  void bkDelete(int id);
}
