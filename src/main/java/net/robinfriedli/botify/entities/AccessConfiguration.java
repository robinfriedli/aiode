package net.robinfriedli.botify.entities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.ISnowflake;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.robinfriedli.jxp.api.AbstractXmlElement;
import net.robinfriedli.jxp.api.XmlElement;
import net.robinfriedli.jxp.persist.Context;
import org.w3c.dom.Element;

public class AccessConfiguration extends AbstractXmlElement {

    public AccessConfiguration(String tagName, Map<String, ?> attributeMap, List<XmlElement> subElements, String textContent, Context context) {
        super(tagName, attributeMap, subElements, textContent, context);
    }

    public AccessConfiguration(String commandIdentifier, List<Role> roles, Context context) {
        super("accessConfiguration", buildAttributes(commandIdentifier, roles), context);
    }

    // invoked by JXP
    @SuppressWarnings("unused")
    public AccessConfiguration(Element element, Context context) {
        super(element, context);
    }

    @Nullable
    @Override
    public String getId() {
        return null;
    }

    public boolean canAccess(Member member) {
        String roleIds = getAttribute("roleIds").getValue();
        List<String> roles = Lists.newArrayList(roleIds.split(","));

        List<String> memberRoles = member.getRoles().stream().map(ISnowflake::getId).collect(Collectors.toList());
        return memberRoles.stream().anyMatch(roles::contains);
    }

    public void setRoles(List<Role> roles) {
        getContext().invoke(() -> setAttribute("roleIds",
            roles.stream().map(ISnowflake::getId).collect(Collectors.joining(","))));
    }

    public void removeRole(Role role) {
        List<String> roleIds = Lists.newArrayList(getAttribute("roleIds").getValue().split(","));

        if (!roleIds.contains(role.getId())) {
            throw new IllegalArgumentException("Configuration does not contain role " + role.getName());
        }

        roleIds.remove(role.getId());
        getContext().invoke(() -> setAttribute("roleIds", String.join(",", roleIds)));
    }

    public void removeRoles(List<Role> roles) {
        List<String> roleIds = Lists.newArrayList(getAttribute("roleIds").getValue().split(","));

        for (Role role : roles) {
            if (!roleIds.contains(role.getId())) {
                throw new IllegalArgumentException("Configuration does not contain role " + role.getName());
            }

            roleIds.remove(role.getId());
        }
        getContext().invoke(() -> setAttribute("roleIds", String.join(",", roleIds)));
    }

    public void addRole(Role role) {
        List<String> roleIds = Lists.newArrayList(getAttribute("roleIds").getValue().split(","));

        if (roleIds.contains(role.getId())) {
            throw new IllegalArgumentException("Configuration already contains role " + role.getName());
        }

        roleIds.add(role.getId());
        getContext().invoke(() -> setAttribute("roleIds", String.join(",", roleIds)));
    }

    public void addRoles(List<Role> roles) {
        List<String> roleIds = Lists.newArrayList(getAttribute("roleIds").getValue().split(","));

        for (Role role : roles) {
            if (roleIds.contains(role.getId())) {
                throw new IllegalArgumentException("Configuration already contains role " + role.getName());
            }

            roleIds.add(role.getId());
        }
        getContext().invoke(() -> setAttribute("roleIds", String.join(",", roleIds)));
    }

    public List<Role> getRoles(Guild guild) {
        String[] roleIds = getAttribute("roleIds").getValue().split(",");
        List<Role> roles = Lists.newArrayList();
        for (String roleId : roleIds) {
            if (!roleId.isEmpty()) {
                roles.add(guild.getRoleById(roleId));
            }
        }

        return roles;
    }

    private static Map<String, ?> buildAttributes(String commandIdentifier, List<Role> roles) {
        Map<String, String> attributeMap = new HashMap<>();
        attributeMap.put("commandIdentifier", commandIdentifier);
        attributeMap.put("roleIds", roles.stream().map(ISnowflake::getId).collect(Collectors.joining(",")));
        return attributeMap;
    }

}
