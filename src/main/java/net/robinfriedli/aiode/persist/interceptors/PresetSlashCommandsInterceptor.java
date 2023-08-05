package net.robinfriedli.aiode.persist.interceptors;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.robinfriedli.aiode.boot.SpringPropertiesConfig;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.concurrent.CompletableFutures;
import net.robinfriedli.aiode.concurrent.ExecutionContext;
import net.robinfriedli.aiode.entities.Preset;
import net.robinfriedli.aiode.function.HibernateInvoker;
import net.robinfriedli.exec.Mode;
import org.hibernate.Interceptor;

import static net.robinfriedli.aiode.boot.tasks.UpsertSlashCommandsTask.*;

public class PresetSlashCommandsInterceptor extends CollectingInterceptor {

    private final CommandManager commandManager;
    private final ExecutionContext executionContext;
    private final HibernateInvoker hibernateInvoker;
    private final SpringPropertiesConfig springPropertiesConfig;

    public PresetSlashCommandsInterceptor(
        Interceptor next,
        Logger logger,
        CommandManager commandManager,
        ExecutionContext executionContext,
        HibernateInvoker hibernateInvoker,
        SpringPropertiesConfig springPropertiesConfig
    ) {
        super(next, logger);
        this.commandManager = commandManager;
        this.executionContext = executionContext;
        this.hibernateInvoker = hibernateInvoker;
        this.springPropertiesConfig = springPropertiesConfig;
    }

    @Override
    public void afterCommit() {
        List<Preset> affectedEntities = getAffectedEntities(Preset.class);
        List<Preset> deletedEntities = getDeletedEntities(Preset.class);

        if (affectedEntities.isEmpty() && deletedEntities.isEmpty()) {
            return;
        }

        if (!Objects.requireNonNullElse(
            springPropertiesConfig.getApplicationProperty(Boolean.class, "aiode.preferences.enable_slash_commands"),
            true
        )) {
            return;
        }

        Guild guild = executionContext.getGuild();

        for (Preset affectedPreset : affectedEntities) {
            if (!SLASH_COMMAND_NAME_PATTERN.matcher(affectedPreset.getIdentifier()).matches()) {
                continue;
            }

            long presetPk = affectedPreset.getPk();
            SLASH_COMMAND_UPDATE_INVOKER.invoke(Mode.getEmpty(), () -> {
                CompletableFuture<Command> future = guild.upsertCommand(affectedPreset.buildSlashCommandData(commandManager)).submit();
                CompletableFutures.thenAccept(future, command -> hibernateInvoker.invokeConsumer(session -> {
                    Preset reloadedPreset = session.getReference(Preset.class, presetPk);
                    reloadedPreset.setCommandId(command.getIdLong());
                }));
            });
        }

        for (Preset deletedPreset : deletedEntities) {
            Long commandId = deletedPreset.getCommandId();
            if (commandId == null) {
                continue;
            }

            SLASH_COMMAND_UPDATE_INVOKER.invoke(Mode.getEmpty(), () ->
                guild.deleteCommandById(commandId).queue()
            );
        }
    }
}
