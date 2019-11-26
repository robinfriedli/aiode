package net.robinfriedli.botify.boot.configurations;

import com.antkorwin.xsync.XSync;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XSync is a useful library that provides a mutex factory which enables synchronisation based on an object's value instead
 * of its physical object. This is especially useful for Strings and avoids having to use {@link String#intern()}.
 */
@Configuration
public class XSyncComponent {

    @Bean
    public XSync<Integer> intXSync() {
        return new XSync<>();
    }

    @Bean
    public XSync<String> xSync() {
        return new XSync<>();
    }
}
