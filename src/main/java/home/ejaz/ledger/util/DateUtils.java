package home.ejaz.ledger.util;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

public class DateUtils {

    public static Date getNextRun(String cronExpression, Date from) {
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
        CronParser parser = new CronParser(cronDefinition);
        try {
            Cron unixCron = parser.parse(cronExpression);
            if (unixCron.validate() != null) {
                ZonedDateTime now = ZonedDateTime.ofInstant(from.toInstant(), ZoneId.systemDefault());
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
