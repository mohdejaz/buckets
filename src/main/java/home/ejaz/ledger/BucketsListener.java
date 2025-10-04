package home.ejaz.ledger;

public interface BucketsListener {

  void txAdded(long id);

  void txUpdate(long id);

  void txDelete(long id);

  void bkAdded(int id);

  void bkUpdate(int id);

  void bkDelete(int id);

  void acctSelected(int id);

  void acctAdded(int id);

  void acctUpdated(int id);

  void acctDeleted(int id);
}
