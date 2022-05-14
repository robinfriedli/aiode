package net.robinfriedli.aiode.command.interceptor.interceptors;

import com.neovisionaries.i18n.CountryCode;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.audio.spotify.SpotifyContext;
import net.robinfriedli.aiode.command.AbstractCommand;
import net.robinfriedli.aiode.command.Command;
import net.robinfriedli.aiode.command.CommandContext;
import net.robinfriedli.aiode.command.interceptor.AbstractChainableCommandInterceptor;
import net.robinfriedli.aiode.command.interceptor.CommandInterceptor;
import net.robinfriedli.aiode.concurrent.ThreadContext;
import net.robinfriedli.aiode.entities.xml.CommandInterceptorContribution;
import net.robinfriedli.aiode.login.Login;
import net.robinfriedli.aiode.login.LoginManager;

public class SpotifyContextInterceptor extends AbstractChainableCommandInterceptor {

    private final LoginManager loginManager;

    public SpotifyContextInterceptor(CommandInterceptorContribution contribution, CommandInterceptor next, LoginManager loginManager) {
        super(contribution, next);
        this.loginManager = loginManager;
    }

    @Override
    public void performChained(Command command) {
        CommandContext context = command.getContext();
        SpotifyContext spotifyContext = ThreadContext.Current.get().getOrCompute(SpotifyContext.class, SpotifyContext::new);
        spotifyContext.setSpotifyApi(context.getSpotifyApi());
        Login loginForUser = loginManager.getLoginForUser(context.getUser());
        spotifyContext.setLogin(loginForUser);

        if (command instanceof AbstractCommand abstractCommand) {
            String market = abstractCommand.getArgumentValueWithTypeOrCompute(
                "market",
                String.class,
                () -> Aiode.get().getSpotifyComponent().getDefaultMarket()
            );

            spotifyContext.setMarket(CountryCode.valueOf(market));
        }
    }
}
