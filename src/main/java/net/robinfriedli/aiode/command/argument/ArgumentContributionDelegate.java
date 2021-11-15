package net.robinfriedli.aiode.command.argument;

import net.robinfriedli.aiode.entities.xml.ArgumentContribution;

/**
 * Interface for any argument configuration type that can resolve to an {@link ArgumentContribution}.
 */
public interface ArgumentContributionDelegate {

    ArgumentContribution unwrapArgumentContribution();

}
