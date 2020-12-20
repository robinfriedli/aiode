package net.robinfriedli.botify.boot.configurations;

import net.robinfriedli.botify.entities.xml.ArgumentContribution;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.botify.entities.xml.CronJobContribution;
import net.robinfriedli.botify.entities.xml.EmbedDocumentContribution;
import net.robinfriedli.botify.entities.xml.GroovyVariableProviderContribution;
import net.robinfriedli.botify.entities.xml.GuildPropertyContribution;
import net.robinfriedli.botify.entities.xml.HttpHandlerContribution;
import net.robinfriedli.botify.entities.xml.StartupTaskContribution;
import net.robinfriedli.botify.entities.xml.Version;
import net.robinfriedli.botify.entities.xml.WidgetContribution;
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
            .build();
    }

}
