package net.robinfriedli.botify.command.argument;

import java.util.List;

import javax.annotation.Nullable;

import net.robinfriedli.botify.command.PermissionTarget;
import net.robinfriedli.botify.entities.xml.ArgumentContribution;
import net.robinfriedli.jxp.api.XmlElement;

public interface ArgumentDefinition {

    /**
     * @return the name of this argument
     */
    String getIdentifier();

    /**
     * @return the description for this argument
     */
    String getDescription();

    /**
     * @return xml elements describing the arguments (with the "argument" attribute) that may not be set if this one is set.
     */
    List<XmlElement> getExcludedArguments();

    /**
     * @return xml elements describing the arguments (with the "argument" attribute) that must be set if this one is set.
     */
    List<XmlElement> getRequiredArguments();

    /**
     * @return xml elements with groovy scripts as text content that describe conditions that must be met if this argument
     * is set, else the text defined by the "errorMessage" attribute is sent as error.
     */
    List<XmlElement> getRules();

    /**
     * @return xml elements with a groovy script in the "check" attribute that describes a condition that must return true
     * when applied to value of this argument (the value is supplied to the groovy shell as the "value" variable),
     * if a value is set for this argument. If the check fails the error defined by the errorMessage attribute is sent.
     */
    List<XmlElement> getValueChecks();

    /**
     * @return the type of value this attribute expects, normally {@link String}.
     */
    Class<?> getValueType();

    /**
     * @return true if this argument always expects a value to be set.
     */
    boolean requiresValue();

    /**
     * @return true if the command is expected to have command input if this argument is set.
     */
    boolean requiresInput();

    /**
     * @return true if this is a statically defined argument defined in the commands.xml configuration file. This is only
     * ever false if this a dynamic undefined argument used for a script invocation to allow scripts to use custom arguments.
     */
    boolean isStatic();

    /**
     * @return the permission target to check access to this argument, returns the {@link ArgumentContribution} if this
     * is a persistent argument definition, i.e. if {@link #isStatic()} returns true, or null.
     */
    @Nullable
    PermissionTarget getPermissionTarget();

}
