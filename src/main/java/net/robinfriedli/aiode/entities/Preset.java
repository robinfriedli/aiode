package net.robinfriedli.aiode.entities;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.robinfriedli.aiode.boot.SpringPropertiesConfig;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.CommandManager;
import net.robinfriedli.aiode.entities.xml.CommandContribution;
import net.robinfriedli.aiode.exceptions.InvalidCommandException;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Table(name = "preset", indexes = {
    @Index(name = "preset_guild_id_idx", columnList = "guild_id"),
    @Index(name = "preset_user_id_idx", columnList = "user_id"),
    @Index(name = "preset_name_idx", columnList = "name"),
})
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Preset implements Serializable, SanitizedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "name")
    @Size(min = 1, max = 30, message = "Invalid length of preset name. Needs to be between 1 and 30.")
    private String name;
    @Column(name = "preset", length = 1000)
    @Size(min = 1, max = 1000, message = "Length of preset must be between 1 and 1000.")
    private String preset;
    @Column(name = "guild")
    private String guild;
    @Column(name = "guild_id")
    private String guildId;
    @Column(name = "user_name")
    private String user;
    @Column(name = "user_id")
    private String userId;
    @Column(name = "command_id")
    private Long commandId;

    public Preset() {

    }

    public Preset(String name, String preset, Guild guild, User user) {
        this.name = name;
        this.preset = preset;
        this.guild = guild.getName();
        this.guildId = guild.getId();
        this.user = user.getName();
        this.userId = user.getId();
    }

    public long getPk() {
        return pk;
    }

    public void setPk(long pk) {
        this.pk = pk;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPreset() {
        return preset;
    }

    public void setPreset(String preset) {
        this.preset = preset;
    }

    public String getGuild() {
        return guild;
    }

    public void setGuild(String guild) {
        this.guild = guild;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public AbstractCommand instantiateCommand(CommandManager commandManager, CommandContext context, String input) {
        CommandContribution presetContribution = getCommandContribution(commandManager);

        String commandBody = preset.substring(presetContribution.getIdentifier().length()).trim();
        String var = input.substring(name.length()).trim();
        if (commandBody.contains("%s")) {
            commandBody = String.format(commandBody, var);
        } else if (!var.isBlank()) {
            throw new InvalidCommandException("This preset does not have a variable");
        }

        return presetContribution.instantiate(commandManager, context, commandBody);
    }

    public CommandData buildSlashCommandData(CommandManager commandManager) {
        return getCommandContribution(commandManager).buildSlashCommandData(getIdentifier());
    }

    public CommandContribution getCommandContribution(CommandManager commandManager) {
        CommandContribution presetContribution = commandManager.getCommandContributionForInput(preset);
        if (presetContribution == null) {
            throw new InvalidCommandException("Invalid preset, no command found");
        }
        return presetContribution;
    }

    @Override
    public int getMaxEntityCount(SpringPropertiesConfig springPropertiesConfig) {
        Integer applicationProperty = springPropertiesConfig.getApplicationProperty(Integer.class, "aiode.preferences.preset_count_max");
        return Objects.requireNonNullElse(applicationProperty, 0);
    }

    @Override
    public String getIdentifierPropertyName() {
        return "name";
    }

    @Override
    public String getIdentifier() {
        return getName();
    }

    @Override
    public void setSanitizedIdentifier(String sanitizedIdentifier) {
        setName(sanitizedIdentifier);
    }

    public Long getCommandId() {
        return commandId;
    }

    public void setCommandId(Long commandId) {
        this.commandId = commandId;
    }
}
