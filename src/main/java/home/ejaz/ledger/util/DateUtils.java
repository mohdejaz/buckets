package home.ejaz.ledger.util;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DateUtils {
    private static final Logger logger = LogManager.getLogger(DateUtils.class.getName());

//    public static Date getNextRun(String cronExpression, Date from) {
//        logger.info("expr: " + cronExpression + " from: " + new SimpleDateFormat("yyyy-MM-dd").format(from));
//        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
//        CronParser parser = new CronParser(cronDefinition);
//        try {
//            Cron unixCron = parser.parse(cronExpression);
//            if (unixCron.validate() != null) {
//                ZonedDateTime now = from != null ? ZonedDateTime.ofInstant(from.toInstant(), ZoneId.systemDefault())
//                        : ZonedDateTime.now();
//                Optional<ZonedDateTime> nextExec = ExecutionTime.forCron(unixCron).nextExecution(now);
//
//                if (nextExec.isPresent()) {
//                    return Date.from(nextExec.get().toInstant());
//                }
//            }
//        } catch (Exception e) {
//            logger.warn("Error", e);
//        }
//        return null;
//    }
}
