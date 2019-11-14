package net.robinfriedli.botify.entities.xml;

import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

import javax.annotation.Nonnull;

import net.robinfriedli.botify.cron.AbstractCronTask;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class CronJobContribution extends GenericClassContribution<AbstractCronTask> {

    // invoked by JXP
    @SuppressWarnings("unused")
    public CronJobContribution(Element element, Context context) {
        super(element, context);
    }

    // invoked by JXP
    @SuppressWarnings("unused")
    public CronJobContribution(Element element, List<XmlElement> subElements, Context context) {
        super(element, subElements, context);
    }

    @Override
    @Nonnull
    public String getId() {
        return getAttribute("id").getValue();
    }

    public String getCronExpression() {
        return getAttribute("cron").getValue();
    }

    public TimeZone getTimeZone() {
        if (hasAttribute("timeZone")) {
            return TimeZone.getTimeZone(ZoneId.of(getAttribute("timeZone").getValue(), ZoneId.SHORT_IDS));
        }
        return TimeZone.getTimeZone(ZoneId.systemDefault());
    }

}
