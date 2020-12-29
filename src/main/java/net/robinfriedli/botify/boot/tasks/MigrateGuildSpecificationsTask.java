package net.robinfriedli.botify.boot.tasks;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.common.base.Splitter;
import com.google.common.io.Files;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.robinfriedli.botify.boot.StartupTask;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.GrantedRole;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.StartupTaskContribution;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import static net.robinfriedli.jxp.queries.Conditions.*;

/**
 * Migrates old XML {@link GuildSpecification}s to the database for the v1.6 update
 */
public class MigrateGuildSpecificationsTask implements StartupTask {

    private final JxpBackend jxpBackend;
    private final SessionFactory sessionFactory;
    private final StartupTaskContribution contribution;

    private final Splitter splitter = Splitter.on(",").trimResults().omitEmptyStrings();

    public MigrateGuildSpecificationsTask(JxpBackend jxpBackend, SessionFactory sessionFactory, StartupTaskContribution contribution) {
        this.jxpBackend = jxpBackend;
        this.sessionFactory = sessionFactory;
        this.contribution = contribution;
    }

    @Override
    public StartupTaskContribution getContribution() {
        return contribution;
    }

    @Override
    public void perform(@Nullable JDA shard) throws IOException {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            File file = new File("./resources/guildSpecifications.xml");
            if (file.exists()) {
                migrateSpecifications(shard, file, session);
            }
            session.getTransaction().commit();
        }
    }

    private void migrateSpecifications(JDA shard, File file, Session session) throws IOException {
        try (Context context = jxpBackend.getContext(file)) {
            List<XmlElement> guildSpecifications = context.query(tagName("guildSpecification")).collect();
            for (XmlElement guildSpecification : guildSpecifications) {
                migrateSpecification(shard, guildSpecification, session);
            }
        }

        File directory = new File("./resources/archive");
        if (!directory.exists()) {
            directory.mkdir();
        }
        Objects.requireNonNull(file);
        Files.move(file, new File(directory.getPath() + "/" + file.getName()));
    }

    private void migrateSpecification(JDA shard, XmlElement specification, Session session) {
        String guildId = specification.getAttribute("guildId").getValue();

        Guild guild = shard.getGuildById(guildId);

        if (guild != null) {
            Optional<GuildSpecification> existingGuildSpecification = session.createQuery("from " + GuildSpecification.class.getName() +
                " where guildId = '" + guild.getId() + "'", GuildSpecification.class).uniqueResultOptional();
            if (existingGuildSpecification.isPresent()) {
                return;
            }

            String botifyName = specification.getAttribute("botifyName").getValue();
            String guildName = specification.getAttribute("guildName").getValue();
            String prefix;
            if (specification.hasAttribute("prefix")) {
                prefix = specification.getAttribute("prefix").getValue();
            } else {
                prefix = null;
            }
            GuildSpecification guildSpecification = new GuildSpecification();
            guildSpecification.setGuildName(guildName);
            guildSpecification.setGuildId(guildId);
            guildSpecification.setBotName(botifyName);
            guildSpecification.setPrefix(prefix);

            List<XmlElement> accessConfigurations = specification.query(tagName("accessConfiguration")).collect();
            for (XmlElement accessConfiguration : accessConfigurations) {
                String commandIdentifier = accessConfiguration.getAttribute("commandIdentifier").getValue();
                AccessConfiguration ac = new AccessConfiguration(commandIdentifier, session);
                String roleIdString = accessConfiguration.getAttribute("roleIds").getValue();
                Iterable<String> roleIds = splitter.split(roleIdString);

                for (String roleId : roleIds) {
                    Role role = guild.getRoleById(roleId);
                    if (role != null) {
                        GrantedRole grantedRole = new GrantedRole(role);
                        session.persist(grantedRole);
                        ac.addRole(grantedRole);
                    }
                }

                guildSpecification.addAccessConfiguration(ac);
                session.persist(ac);
            }

            session.persist(guildSpecification);
        }
    }

}
