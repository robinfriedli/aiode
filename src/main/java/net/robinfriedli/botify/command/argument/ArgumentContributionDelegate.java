package net.robinfriedli.botify.command.argument;

import net.robinfriedli.botify.entities.xml.ArgumentContribution;

/**
 * Interface for any argument configuration type that can resolve to an {@link ArgumentContribution}.
 */
public interface ArgumentContributionDelegate {

    ArgumentContribution unwrapArgumentContribution();

}
