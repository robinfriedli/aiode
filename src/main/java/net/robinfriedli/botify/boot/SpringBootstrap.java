package net.robinfriedli.botify.boot;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.cron.CronJobService;
import net.robinfriedli.botify.entities.xml.StartupTaskContribution;
import net.robinfriedli.botify.servers.HttpServerManager;
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
            JxpBackend jxpBackend = botify.getJxpBackend();
            CronJobService cronJobService = botify.getCronJobService();

            commandManager.initializeInterceptorChain();
            serverManager.start();

            // run startup tasks
            InputStream startupTasksFile = getClass().getResourceAsStream("/xml-contributions/startupTasks.xml");
            Context context = jxpBackend.createContext(startupTasksFile);
            for (StartupTaskContribution element : context.getInstancesOf(StartupTaskContribution.class)) {
                if (!element.getAttribute("runForEachShard").getBool()) {
                    element.instantiate().runTask(null);
                }
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
