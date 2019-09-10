package net.robinfriedli.botify.entities.xml;

import java.util.List;

import javax.annotation.Nullable;

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
    public String getId() {
        return getAttribute("id").getValue();
    }

    public String getCronExpression() {
        return getAttribute("cron").getValue();
    }

    public static class CronParameter extends GenericClassContribution<Object> {

        // invoked by JXP
        @SuppressWarnings("unused")
        public CronParameter(Element element, Context context) {
            super(element, context);
        }

        @Nullable
        @Override
        public String getId() {
            return getName();
        }

        public String getName() {
            return getAttribute("name").getValue();
        }
    }

}
