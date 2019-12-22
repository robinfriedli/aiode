package net.robinfriedli.botify.boot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.cron.CronJobService;
import net.robinfriedli.botify.discord.CommandExecutionQueueManager;
import net.robinfriedli.botify.discord.GuildManager;
import net.robinfriedli.botify.entities.xml.StartupTaskContribution;
import net.robinfriedli.botify.function.CheckedConsumer;
import net.robinfriedli.botify.servers.HttpServerManager;
import net.robinfriedli.botify.persist.StaticSessionProvider;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties
@SpringBootApplication
public class SpringBootstrap implements CommandLineRunner {

    public static void main(String[] args) {
        try {
            SpringApplication.run(SpringBootstrap.class, args);
        } catch (Throwable e) {
            Logger logger = LoggerFactory.getLogger(SpringBootstrap.class);
            logger.error("Exception starting SpringApplication, AppContext could not be created.", e);
            System.exit(1);
        }
    }

    @Override
    public void run(String... args) {
        Logger logger = LoggerFactory.getLogger(SpringBootstrap.class);
        logger.info("Using java version " + System.getProperty("java.runtime.version"));
        try {
            Botify botify = Botify.get();
            CommandManager commandManager = botify.getCommandManager();
            HttpServerManager serverManager = botify.getHttpServerManager();
            ShardManager shardManager = botify.getShardManager();
            GuildManager guildManager = botify.getGuildManager();
            CommandExecutionQueueManager executionQueueManager = botify.getExecutionQueueManager();
            JxpBackend jxpBackend = botify.getJxpBackend();
            CronJobService cronJobService = botify.getCronJobService();

            commandManager.initializeInterceptorChain();
            serverManager.start();

            shardManager.getShards().forEach((CheckedConsumer<JDA>) JDA::awaitReady);

            // setup guilds
            StaticSessionProvider.invokeWithSession(session -> {
                // setup current thread session and handle all guilds within one session instead of opening a new session for each
                for (Guild guild : shardManager.getGuilds()) {
                    guildManager.addGuild(guild);
                    executionQueueManager.addGuild(guild);
                }
            });

            // run startup tasks
            String startupTasksFile = getClass().getResource("/xml-contributions/startupTasks.xml").getFile();
            Context context = jxpBackend.getContext(startupTasksFile);
            for (StartupTaskContribution element : context.getInstancesOf(StartupTaskContribution.class)) {
                element.instantiate().perform();
            }

            cronJobService.scheduleAll();

            Botify.registerListeners();
            logger.info("All starters done");
        } catch (Throwable e) {
            logger.error("Exception in starter. Application will terminate.", e);
            System.exit(1);
        }
    }
}
