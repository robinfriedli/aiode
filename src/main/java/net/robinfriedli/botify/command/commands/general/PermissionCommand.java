package net.robinfriedli.botify.command.commands.general;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.botify.Botify;
import net.robinfriedli.botify.command.AbstractCommand;
import net.robinfriedli.botify.command.CommandContext;
import net.robinfriedli.botify.command.CommandManager;
import net.robinfriedli.botify.command.PermissionTarget;
import net.robinfriedli.botify.command.SecurityManager;
import net.robinfriedli.botify.command.argument.CommandArgument;
import net.robinfriedli.botify.command.widget.widgets.PermissionListPaginationWidget;
import net.robinfriedli.botify.discord.GuildContext;
import net.robinfriedli.botify.entities.AccessConfiguration;
import net.robinfriedli.botify.entities.CustomPermissionTarget;
import net.robinfriedli.botify.entities.GrantedRole;
import net.robinfriedli.botify.entities.GuildSpecification;
import net.robinfriedli.botify.entities.xml.CommandContribution;
import net.robinfriedli.botify.exceptions.InvalidCommandException;
import net.robinfriedli.botify.exceptions.NoResultsFoundException;
import org.hibernate.Session;

public class PermissionCommand extends AbstractCommand {

    private static final Splitter COMMA_SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings();

    public PermissionCommand(CommandContribution commandContribution, CommandContext context, CommandManager commandManager, String commandString, boolean requiresInput, String identifier, String description, Category category) {
        super(commandContribution, context, commandManager, commandString, requiresInput, identifier, description, category);
    }

    @Override
    public void doRun() {
        if (argumentSet("grant")) {
            grantPermissions();
        } else if (argumentSet("deny")) {
            denyPermissions();
        } else if (argumentSet("clear")) {
            clearCommands();
        } else if (argumentSet("lock")) {
            lockCommands();
        } else if (argumentSet("create")) {
            createCustomPermissionTarget();
        } else if (argumentSet("delete")) {
            deleteCustomPermissionTarget();
        } else if (argumentSet("targets")) {
            listTargets();
        } else {
            listPermissions();
        }
    }

    private void listPermissions() {
        CommandContext context = getContext();
        Guild guild = context.getGuild();
        GuildContext guildContext = context.getGuildContext();
        SecurityManager securityManager = Botify.get().getSecurityManager();

        List<PermissionTarget> targets = Lists.newArrayList();

        for (PermissionTarget.TargetType targetType : PermissionTarget.TargetType.values()) {
            if (!targetType.isChildTargetOnly()) {
                Set<? extends PermissionTarget> permissionTargetsInCategory = targetType.getAllPermissionTargetsInCategory();
                permissionTargetsInCategory
                    .stream()
                    .sorted(
                        Comparator
                            .comparingInt((ToIntFunction<PermissionTarget>) permissionTarget -> permissionTarget.getPermissionTypeCategory().getSorting())
                            .thenComparing(PermissionTarget::getPermissionTargetIdentifier)
                    )
                    .forEach(permissionTarget -> {
                        List<PermissionTarget> flattenedPermissionHierarchy = Lists.newArrayList();
                        LinkedList<List<PermissionTarget>> stack = new LinkedList<>();
                        stack.push(flattenedPermissionHierarchy);
                        flattenPermissionTargetHierarchy(permissionTarget, stack, securityManager, guild, true);
                        targets.addAll(flattenedPermissionHierarchy);
                    });
            }
        }

        PermissionListPaginationWidget permissionListPaginationWidget = new PermissionListPaginationWidget(
            guildContext.getWidgetRegistry(),
            guild,
            context.getChannel(),
            targets,
            securityManager
        );

        permissionListPaginationWidget.initialise();
    }

    private void grantPermissions() {
        SecurityManager securityManager = Botify.get().getSecurityManager();
        CommandContext context = getContext();
        GuildSpecification specification = context.getGuildContext().getSpecification();
        Session session = context.getSession();
        Set<? extends PermissionTarget> selectedTargets = getSelectedTargets();
        Set<Role> selectedRoles = getSelectedRoles("to");

        boolean addedAnything = invoke(() -> {
            boolean takenAction = false;
            for (PermissionTarget permissionTarget : selectedTargets) {
                Optional<AccessConfiguration> existingConfiguration = securityManager.getAccessConfiguration(permissionTarget, context.getGuild());
                if (existingConfiguration.isPresent()) {
                    AccessConfiguration accessConfiguration = existingConfiguration.get();
                    Set<String> roleIds = accessConfiguration.getRoleIds();
                    for (Role selectedRole : selectedRoles) {
                        if (roleIds.contains(selectedRole.getId())) {
                            continue;
                        }

                        GrantedRole role = new GrantedRole(selectedRole);
                        session.persist(role);
                        accessConfiguration.addRole(role);
                        takenAction = true;
                    }
                } else {
                    AccessConfiguration accessConfiguration = new AccessConfiguration(permissionTarget, session);
                    for (Role selectedRole : selectedRoles) {
                        GrantedRole role = new GrantedRole(selectedRole);
                        session.persist(role);
                        accessConfiguration.addRole(role);
                    }

                    session.persist(accessConfiguration);
                    specification.addAccessConfiguration(accessConfiguration);
                    takenAction = true;
                }

            }
            return takenAction;
        });

        if (!addedAnything) {
            sendError("All selected roles have already been granted each selected permission. No changes needed.");
        }
    }

    private void denyPermissions() {
        SecurityManager securityManager = Botify.get().getSecurityManager();
        CommandContext context = getContext();
        Set<? extends PermissionTarget> selectedCommands = getSelectedTargets();
        Set<Role> selectedRoles = getSelectedRoles("for");
        Session session = context.getSession();

        boolean removedAnything = invoke(() -> {
            boolean takenAction = false;
            for (PermissionTarget permissionTarget : selectedCommands) {
                Optional<AccessConfiguration> existingAccessConfiguration = securityManager.getAccessConfiguration(permissionTarget, context.getGuild());
                if (existingAccessConfiguration.isPresent()) {
                    AccessConfiguration accessConfiguration = existingAccessConfiguration.get();

                    for (Role selectedRole : selectedRoles) {
                        Optional<GrantedRole> setRole = accessConfiguration.getRole(selectedRole.getId());
                        if (setRole.isPresent()) {
                            GrantedRole grantedRole = setRole.get();
                            accessConfiguration.removeRole(grantedRole);
                            session.delete(grantedRole);
                            takenAction = true;
                        }
                    }
                }
            }

            return takenAction;
        });

        if (!removedAnything) {
            sendError("None of the selected roles were granted any of the selected permissions. The deny argument is used " +
                "to remove granted roles from permissions. If there currently aren't any restrictions for the selected permissions " +
                "use the grant argument to limit the permission to certain roles.");
        }
    }

    private void clearCommands() {
        SecurityManager securityManager = Botify.get().getSecurityManager();
        GuildSpecification specification = getContext().getGuildContext().getSpecification();
        Set<? extends PermissionTarget> selectedCommands = getSelectedTargets();
        Session session = getContext().getSession();

        boolean removedAnything = invoke(() -> {
            boolean takenAction = false;
            for (PermissionTarget permissionTarget : selectedCommands) {
                Optional<AccessConfiguration> accessConfiguration = securityManager.getAccessConfiguration(permissionTarget, getContext().getGuild());
                if (accessConfiguration.isPresent()) {
                    AccessConfiguration ac = accessConfiguration.get();
                    takenAction = specification.removeAccessConfiguration(ac);
                    ac.getRoles().forEach(session::delete);
                    session.delete(ac);
                }
            }

            return takenAction;
        });

        if (!removedAnything) {
            sendError("None of the selected permissions had an access configuration set up to begin with. No action required.");
        }
    }

    private void lockCommands() {
        SecurityManager securityManager = Botify.get().getSecurityManager();
        GuildSpecification specification = getContext().getGuildContext().getSpecification();
        Set<? extends PermissionTarget> selectedCommands = getSelectedTargets();
        Session session = getContext().getSession();

        boolean lockedAnything = invoke(() -> {
            boolean takenAction = false;
            for (PermissionTarget permissionTarget : selectedCommands) {
                Optional<AccessConfiguration> existingAccessConfiguration = securityManager.getAccessConfiguration(permissionTarget, getContext().getGuild());
                if (existingAccessConfiguration.isPresent()) {
                    AccessConfiguration accessConfiguration = existingAccessConfiguration.get();
                    Set<GrantedRole> setRoles = accessConfiguration.getRoles();
                    for (GrantedRole setRole : setRoles) {
                        accessConfiguration.removeRole(setRole);
                        session.delete(setRole);
                        takenAction = true;
                    }
                } else {
                    AccessConfiguration accessConfiguration = new AccessConfiguration(permissionTarget, session);
                    session.persist(accessConfiguration);
                    specification.addAccessConfiguration(accessConfiguration);
                    takenAction = true;
                }
            }

            return takenAction;
        });

        if (!lockedAnything) {
            sendError("All selected permissions are already only available to the guild owner and administrator roles.");
        }
    }

    private void createCustomPermissionTarget() {
        SecurityManager securityManager = Botify.get().getSecurityManager();
        String identifier = getCommandInput();
        CommandContext context = getContext();
        Session session = context.getSession();

        Optional<? extends PermissionTarget> existingPermissionTarget = securityManager.getPermissionTarget(identifier);
        if (existingPermissionTarget.isPresent()) {
            throw new InvalidCommandException(String.format("Permission target '%s' already exists.", identifier));
        }

        Guild guild = context.getGuild();
        User user = context.getUser();
        CustomPermissionTarget customPermissionTarget = new CustomPermissionTarget(identifier, guild, user);
        invoke(() -> session.persist(customPermissionTarget));
    }

    private void deleteCustomPermissionTarget() {
        SecurityManager securityManager = Botify.get().getSecurityManager();
        String identifier = getCommandInput();
        CommandContext context = getContext();
        Session session = context.getSession();

        Optional<? extends PermissionTarget> existingPermissionTarget = securityManager.getPermissionTarget(identifier);
        if (existingPermissionTarget.isEmpty()) {
            throw new InvalidCommandException(String.format("No such permission target '%s'.", identifier));
        }

        PermissionTarget permissionTarget = existingPermissionTarget.get();
        if (!(permissionTarget instanceof CustomPermissionTarget)) {
            throw new InvalidCommandException(String.format("Permission target '%s' cannot be deleted as it is not a custom target.", identifier));
        }

        CustomPermissionTarget customPermissionTarget = (CustomPermissionTarget) permissionTarget;

        invoke(() -> {
            Optional<AccessConfiguration> accessConfiguration = securityManager.getAccessConfiguration(customPermissionTarget, context.getGuild());
            if (accessConfiguration.isPresent()) {
                for (GrantedRole role : accessConfiguration.get().getRoles()) {
                    session.delete(role);
                }
                session.delete(accessConfiguration);
            }

            session.delete(customPermissionTarget);
        });
    }

    private void listTargets() {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Permission targets");
        embedBuilder.setDescription("All possible targets for which permissions can be managed including all commands and " +
            "custom targets grouped by their category. Except for the arguments category these categories can be used with the $category argument.");

        List<MessageEmbed.Field> fields = Lists.newArrayList();

        for (PermissionTarget.TargetType targetType : PermissionTarget.TargetType.values()) {
            collectCategoryEmbedFields(fields, targetType);
        }

        for (MessageEmbed.Field field : fields) {
            embedBuilder.addField(field);
        }

        sendMessage(embedBuilder);
    }

    private Set<Role> getSelectedRoles(String argument) {
        String roleString;
        if (argumentSet("all")) {
            roleString = getArgumentValueOrElse(argument, getCommandInput());
        } else {
            roleString = getArgumentValue(argument);
        }

        Guild guild = getContext().getGuild();
        Set<Role> selectedRoles = Sets.newHashSet();
        List<String> roles = COMMA_SPLITTER.splitToList(roleString);

        for (String role : roles) {
            List<Role> rolesByName = guild.getRolesByName(role, true);
            if (rolesByName.isEmpty()) {
                throw new InvalidCommandException("No such role " + role);
            }
            selectedRoles.addAll(rolesByName);
        }

        if (selectedRoles.isEmpty()) {
            throw new InvalidCommandException("No roles selected. Either use the $to argument or, if the $all argument is used, provide the roles as command input.");
        }

        return selectedRoles;
    }

    private Set<? extends PermissionTarget> getSelectedTargets() {
        Set<? extends PermissionTarget> selectedRootTargets = getSelectedRootTargets();

        if (!argumentSet("argument")) {
            return selectedRootTargets;
        }

        String argumentValue;
        if (argumentSet("all")) {
            argumentValue = getArgumentValueOrElse("argument", getCommandInput());
        } else {
            argumentValue = getArgumentValueWithTypeOrElse("argument", String.class, null);
        }
        if (argumentValue == null) {
            return getAllArguments(selectedRootTargets);
        }
        List<String> argumentIdentifiers = COMMA_SPLITTER.splitToList(argumentValue);
        if (argumentIdentifiers.isEmpty()) {
            return getAllArguments(selectedRootTargets);
        }

        Set<CommandArgument> selectedArguments = Sets.newHashSet();

        for (String argumentIdentifier : argumentIdentifiers) {
            for (PermissionTarget selectedRootTarget : selectedRootTargets) {
                if (selectedRootTarget instanceof CommandContribution) {
                    CommandContribution commandContribution = (CommandContribution) selectedRootTarget;

                    CommandArgument argument = commandContribution.getArgument(argumentIdentifier);
                    if (argument != null) {
                        selectedArguments.add(argument);
                    } else {
                        throw new InvalidCommandException(
                            String.format(
                                "No such argument '%s' on command '%s'.",
                                argumentIdentifier,
                                commandContribution.getIdentifier()
                            )
                        );
                    }
                } else {
                    throw new InvalidCommandException(
                        String.format(
                            "Cannot find argument '%s' on permission target '%s' as it is not a command.",
                            argumentIdentifier,
                            selectedRootTarget.getFullPermissionTargetIdentifier()
                        )
                    );
                }
            }
        }

        return selectedArguments;
    }

    private Set<? extends PermissionTarget> getSelectedRootTargets() {
        if (argumentSet("all")) {
            return getAllTargets();
        } else if (argumentSet("category")) {
            SecurityManager securityManager = Botify.get().getSecurityManager();
            String categoryString;
            String commandInput = getCommandInput();

            if (commandInput.isBlank()) {
                String argumentValue = getArgumentValueWithTypeOrElse("category", String.class, null);
                if (!Strings.isNullOrEmpty(argumentValue)) {
                    categoryString = argumentValue;
                } else {
                    throw new InvalidCommandException("Argument 'category' set bot no categories provided.");
                }
            } else {
                categoryString = commandInput;
            }

            Set<PermissionTarget> targets = Sets.newHashSet();
            for (String category : COMMA_SPLITTER.splitToList(categoryString)) {
                Optional<Set<? extends PermissionTarget>> permissionTargetsByCategory = securityManager.getPermissionTargetsByCategory(category, true);
                if (permissionTargetsByCategory.isPresent()) {
                    targets.addAll(permissionTargetsByCategory.get());
                } else {
                    throw new InvalidCommandException(String.format("No such permission target category or command category '%s'.", category));
                }
            }

            if (targets.isEmpty()) {
                throw new NoResultsFoundException(String.format("No targets found in category / categories '%s'", categoryString));
            }

            return targets;
        } else {
            String identifierString = getCommandInput();

            Set<PermissionTarget> selectedTargets = Sets.newHashSet();
            SecurityManager securityManager = Botify.get().getSecurityManager();
            for (String identifier : COMMA_SPLITTER.splitToList(identifierString)) {
                PermissionTarget permissionTarget = securityManager
                    .getPermissionTarget(identifier)
                    .orElseThrow(() -> new InvalidCommandException("No such command, argument or custom permission " + identifier));
                selectedTargets.add(permissionTarget);
            }

            return selectedTargets;
        }
    }

    private Set<CommandArgument> getAllArguments(Set<? extends PermissionTarget> rootTargets) {
        Set<CommandArgument> arguments = Sets.newHashSet();

        for (PermissionTarget rootTarget : rootTargets) {
            if (rootTarget instanceof CommandContribution) {
                arguments.addAll(((CommandContribution) rootTarget).getArguments().values());
            } else {
                throw new InvalidCommandException(
                    String.format(
                        "Cannot get arguments from permission target '%s' as it is not a command.",
                        rootTarget.getFullPermissionTargetIdentifier()
                    )
                );
            }
        }

        if (arguments.isEmpty()) {
            throw new NoResultsFoundException("Could not find any arguments for the selected commands.");
        }

        return arguments;
    }

    private Set<PermissionTarget> getAllTargets() {
        Set<PermissionTarget> allTargets = Sets.newHashSet();

        for (PermissionTarget.TargetType targetType : PermissionTarget.TargetType.values()) {
            if (!targetType.isChildTargetOnly()) {
                allTargets.addAll(targetType.getAllPermissionTargetsInCategory());
            }
        }

        if (allTargets.isEmpty()) {
            throw new NoResultsFoundException("No permission targets found");
        }

        return allTargets;
    }

    /**
     * Collect a permission target with all of its recursive child targets into a flat list. This function calls itself
     * recursively for all child targets after pushing a new list to the stack. If the current target is relevant, either
     * because it is the root target or because an access configuration exists for the target, the target is added to
     * the current list at the head of the stack. After calling the function for all child targets the stack is popped
     * and the returned list is appended to the list of the current head of the stack. If the current target was found to
     * be irrelevant but the returned list from the stack element of the child targets is not empty, the current target
     * is added before the relevant child elements.
     * <p>
     * E.g.:
     *
     * <pre>
     *     parent 1
     *      - irrelevant child 1
     *        - irrelevant child 11
     *          - relevant child 111
     *          - irrelevant child 112
     *        - relevant child 12
     *          - irrelevant child 121
     *          - relevant child 122
     *        - irrelevant child 13
     *      - relevant child 2
     *     parent 2
     *      - relevant child 1
     *        - relevant child 11
     *        - relevant child 12
     *      - irrelevant child 2
     *        - irrelevant child 21
     *        - irrelevant child 22
     *      - relevant child 3
     *     parent 3
     *      - irrelevant child
     *     parent 4
     * </pre>
     * <p>
     * becomes:
     * <pre>
     *     parent1
     *     irrelevant child 1
     *     irrelevant child 11
     *     relevant child 111
     *     relevant child 12
     *     relevant child 122
     *     relevant child 2
     *     parent2
     *     relevant child 1
     *     relevant child 11
     *     relevant child 12
     *     relevant child 3
     *     parent3
     *     parent4
     * </pre>
     *
     * @param currentTarget   the current permission target, originally the root target, then child targets for recursive calls
     * @param currentStack    stack managing recursive calls the pushing a new list per child level
     * @param securityManager the security manager used to find access configurations
     * @param guild           the current guild
     * @param isRoot          true if method is being called with the root element
     */
    private void flattenPermissionTargetHierarchy(
        PermissionTarget currentTarget,
        LinkedList<List<PermissionTarget>> currentStack,
        SecurityManager securityManager,
        Guild guild,
        boolean isRoot
    ) {
        List<PermissionTarget> currentFrame = currentStack.peek();

        if (currentFrame == null) {
            throw new IllegalStateException("No current stack frame");
        }

        boolean isRelevant = isRoot || securityManager.hasAccessConfiguration(currentTarget, guild);
        if (isRelevant) {
            currentFrame.add(currentTarget);
        }

        Set<? extends PermissionTarget> children = currentTarget.getChildren();
        if (children != null && !children.isEmpty()) {
            currentStack.push(Lists.newArrayList());

            children.stream().sorted(Comparator.comparing(PermissionTarget::getPermissionTargetIdentifier)).forEach(child ->
                flattenPermissionTargetHierarchy(child, currentStack, securityManager, guild, false));

            List<PermissionTarget> stackFrame = currentStack.pop();
            if (!stackFrame.isEmpty()) {
                // target was not relevant but has relevant children, add it first
                if (!isRelevant) {
                    currentFrame.add(currentTarget);
                }
                currentFrame.addAll(stackFrame);
            }
        }
    }

    private void collectCategoryEmbedFields(List<MessageEmbed.Field> fields, PermissionTarget.PermissionTypeCategory category) {
        MessageEmbed.Field embedField = category.createEmbedField();

        if (embedField != null) {
            fields.add(embedField);
        }

        PermissionTarget.PermissionTypeCategory[] subCategories = category.getSubCategories();
        if (subCategories != null) {
            for (PermissionTarget.PermissionTypeCategory subCategory : subCategories) {
                collectCategoryEmbedFields(fields, subCategory);
            }
        }
    }

    @Override
    public void onSuccess() {
        // success message sent by interceptor
    }

}
