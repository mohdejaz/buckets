package home.ejaz.ledger.util;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.apache.log4j.Logger;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

public class DateUtils {
    private static final Logger logger = Logger.getLogger(DateUtils.class.getName());

    public static Date getNextRun(String cronExpression, Date from) {
        logger.info("expr: " + cronExpression + " from: " + new SimpleDateFormat("yyyy-MM-dd").format(from));
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
        CronParser parser = new CronParser(cronDefinition);
        try {
            Cron unixCron = parser.parse(cronExpression);
            if (unixCron.validate() != null) {
                ZonedDateTime now = from != null ? ZonedDateTime.ofInstant(from.toInstant(), ZoneId.systemDefault())
                        : ZonedDateTime.now();
                Optional<ZonedDateTime> nextExec = ExecutionTime.forCron(unixCron).nextExecution(now);

                if (nextExec.isPresent()) {
                    return Date.from(nextExec.get().toInstant());
                }
            }
        } catch (Exception e) {
            logger.warn("Error", e);
        }
        return null;
    }
}
