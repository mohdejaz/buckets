package home.ejaz.ledger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Context {
    private static final Logger logger = LogManager.getLogger(Context.class.getName());

    private static Integer userId = 1; // default value
    private static Integer acctId;

    public static Integer getUserId() {
        return userId;
    }

    public static Integer getAcctId() {
        return acctId;
    }

    public static synchronized void setAcctId(Integer acctId) {
        logger.debug("Old Acct: {}, New Acct: {}", Context.acctId, acctId);
        Context.acctId = acctId;
    }
}
