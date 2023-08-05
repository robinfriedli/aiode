package net.robinfriedli.aiode.boot.tasks;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import groovy.lang.Tuple2;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.robinfriedli.aiode.boot.SpringPropertiesConfig;
import net.robinfriedli.aiode.boot.StartupTask;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.concurrent.CompletableFutures;
import net.robinfriedli.aiode.entities.Preset;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.entities.xml.StartupTaskContribution;
import net.robinfriedli.aiode.function.HibernateInvoker;
import net.robinfriedli.aiode.function.RateLimitInvoker;
import net.robinfriedli.aiode.persist.qb.PredicateBuilder;
import net.robinfriedli.aiode.persist.qb.QueryBuilderFactory;
import net.robinfriedli.exec.Mode;
import org.jetbrains.annotations.Nullable;

public class UpsertSlashCommandsTask implements StartupTask {

    public static final RateLimitInvoker SLASH_COMMAND_UPDATE_INVOKER = new RateLimitInvoker("slash_command_update", 2, Duration.ofSeconds(1), Duration.ofMinutes(300));
    public static final Pattern SLASH_COMMAND_NAME_PATTERN = Pattern.compile("^[-_\\p{L}\\p{N}]{1,32}$");

    private static boolean GLOBAL_LIST_UPDATED = false;

    private final HibernateInvoker hibernateInvoker = new HibernateInvoker();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CommandManager commandManager;
    private final QueryBuilderFactory queryBuilderFactory;
    private final SpringPropertiesConfig springPropertiesConfig;
    private final StartupTaskContribution contribution;

    public UpsertSlashCommandsTask(
        CommandManager commandManager,
        QueryBuilderFactory queryBuilderFactory,
        SpringPropertiesConfig springPropertiesConfig,
        StartupTaskContribution contribution
    ) {
        this.commandManager = commandManager;
        this.queryBuilderFactory = queryBuilderFactory;
        this.springPropertiesConfig = springPropertiesConfig;
        this.contribution = contribution;
    }

    @Override
    public void perform(@Nullable JDA shard) throws Exception {
        if (!Objects.requireNonNullElse(
            springPropertiesConfig.getApplicationProperty(Boolean.class, "aiode.preferences.enable_slash_commands"),
            true
        )) {
            return;
        }

        Objects.requireNonNull(shard);
        logger.info("Updating slash commands for shard " + shard);
        if (!GLOBAL_LIST_UPDATED) {
            List<CommandContribution> commandContributions = commandManager.getCommandContributions();
            List<CommandData> slashCommandData = commandContributions.stream().map(CommandContribution::buildSlashCommandData).collect(Collectors.toList());
            shard.updateCommands().addCommands(slashCommandData).queue();
            GLOBAL_LIST_UPDATED = true;
        }

        hibernateInvoker.invokeConsumer(session -> {
            for (Guild guild : shard.getGuilds()) {
                PredicateBuilder presetGuildIdCondition = (cb, root, subQueryFactory) -> cb.equal(root.get("guildId"), guild.getId());
                Long guildPresetCount = queryBuilderFactory
                    .count(Preset.class)
                    .where(presetGuildIdCondition)
                    .build(session)
                    .getSingleResult();

                if (guildPresetCount == 0) {
                    continue;
                }

                SLASH_COMMAND_UPDATE_INVOKER.invokeLimited(Mode.getEmpty(), () -> hibernateInvoker.invokeConsumer(guildSession -> {
                    List<Preset> presets = queryBuilderFactory
                        .find(Preset.class)
                        .where(presetGuildIdCondition)
                        .build(guildSession)
                        .getResultList();

                    List<Tuple2<CommandData, Preset>> presetCommandData = presets
                        .stream()
                        .filter(p -> SLASH_COMMAND_NAME_PATTERN.matcher(p.getIdentifier()).matches())
                        .map(p -> Tuple2.tuple(p.buildSlashCommandData(commandManager), p))
                        .toList();

                    List<CommandData> commandsToAdd = Lists.newArrayList();
                    Map<String, Preset> mappedPresets = new HashMap<>();
                    for (Tuple2<CommandData, Preset> presetCommandDatum : presetCommandData) {
                        CommandData commandData = presetCommandDatum.getV1();
                        Preset preset = presetCommandDatum.getV2();
                        if (mappedPresets.putIfAbsent(commandData.getName(), preset) != null) {
                            logger.warn(String.format(
                                "Encountered presets with duplicate slash command identifier '%s' on guild '%s'. Going to delete preset pk %d",
                                commandData.getName(),
                                guild,
                                preset.getPk()
                            ));
                            session.remove(preset);
                        } else {
                            commandsToAdd.add(commandData);
                        }
                    }

                    CompletableFuture<List<Command>> future = guild.updateCommands().addCommands(commandsToAdd).submit();

                    CompletableFutures.thenAccept(future, commands -> hibernateInvoker.invokeConsumer(futureSession -> {
                        for (Command command : commands) {
                            Preset preset = mappedPresets.get(command.getName());
                            if (preset == null) {
                                logger.warn(String.format(
                                    "No preset found for name '%s' on guild %s to associate with",
                                    command.getName(),
                                    guild
                                ));
                                continue;
                            }

                            Preset reloadedPreset = futureSession.getReference(Preset.class, preset.getPk());
                            reloadedPreset.setCommandId(command.getIdLong());
                        }
                    }));
                }));
            }
        });

        logger.info("Done updating slash commands (except for queued operations) for shard " + shard);
    }

    @Override
    public StartupTaskContribution getContribution() {
        return contribution;
    }
}
