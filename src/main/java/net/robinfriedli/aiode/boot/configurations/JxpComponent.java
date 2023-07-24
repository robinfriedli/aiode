package net.robinfriedli.aiode.boot.configurations;

import net.robinfriedli.aiode.entities.xml.ArgumentContribution;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.aiode.entities.xml.CronJobContribution;
import net.robinfriedli.aiode.entities.xml.EmbedDocumentContribution;
import net.robinfriedli.aiode.entities.xml.GroovyVariableProviderContribution;
import net.robinfriedli.aiode.entities.xml.GuildPropertyContribution;
import net.robinfriedli.aiode.entities.xml.HttpHandlerContribution;
import net.robinfriedli.aiode.entities.xml.StartupTaskContribution;
import net.robinfriedli.aiode.entities.xml.Version;
import net.robinfriedli.aiode.entities.xml.WidgetContribution;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.api.JxpBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JxpComponent {

    @Bean
    public JxpBackend getJxpBackend() {
        return new JxpBuilder()
            .mapClass("command", CommandContribution.class)
            .mapClass("abstractCommand", CommandContribution.AbstractCommandContribution.class)
            .mapClass("argument", ArgumentContribution.class)
            .mapClass("commandInterceptor", CommandInterceptorContribution.class)
            .mapClass("httpHandler", HttpHandlerContribution.class)
            .mapClass("embedDocument", EmbedDocumentContribution.class)
            .mapClass("startupTask", StartupTaskContribution.class)
            .mapClass("guildProperty", GuildPropertyContribution.class)
            .mapClass("cronJob", CronJobContribution.class)
            .mapClass("widget", WidgetContribution.class)
            .mapClass("widgetAction", WidgetContribution.WidgetActionContribution.class)
            .mapClass("version", Version.class)
            .mapClass("feature", Version.Feature.class)
            .mapClass("groovyVariableProvider", GroovyVariableProviderContribution.class)
            .mapClass("action-row", WidgetContribution.WidgetActionRow.class)
            .build();
    }

}
