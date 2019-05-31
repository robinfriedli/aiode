package net.robinfriedli.botify.entities;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.User;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.exceptions.InvalidCommandException;

@Entity
@Table(name = "preset")
public class Preset implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk")
    private long pk;
    @Column(name = "name")
    private String name;
    @Column(name = "preset")
    private String preset;
    @Column(name = "guild")
    private String guild;
    @Column(name = "guild_id")
    private String guildId;
    @Column(name = "user_name")
    private String user;
    @Column(name = "user_id")
    private String userId;

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
        CommandContribution presetContribution = commandManager.getCommandContributionForInput(preset);
        if (presetContribution == null) {
            throw new InvalidCommandException("Invalid preset, no command found");
        }

        String commandBody = preset.substring(presetContribution.getIdentifier().length()).trim();
        String var = input.substring(name.length()).trim();
        if (commandBody.contains("%s")) {
            commandBody = String.format(commandBody, var);
        } else if (!var.isBlank()) {
            throw new InvalidCommandException("This preset does not have a variable");
        }

        return presetContribution.instantiate(commandManager, context, commandBody);
    }
}
