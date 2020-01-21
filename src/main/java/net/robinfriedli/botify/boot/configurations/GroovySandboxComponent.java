package net.robinfriedli.botify.boot.configurations;

import java.io.IOException;

import groovy.lang.GroovyShell;
import net.robinfriedli.botify.scripting.GroovyWhitelistInterceptor;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.kohsuke.groovy.sandbox.SandboxTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class GroovySandboxComponent {

    private final Context whitelistConfiguration;

    public GroovySandboxComponent(@Value("classpath:xml-contributions/groovyWhitelist.xml") Resource whitelistResource, JxpBackend jxpBackend) {
        try {
            this.whitelistConfiguration = jxpBackend.getContext(whitelistResource.getFile());
        } catch (IOException e) {
            throw new RuntimeException("Could not instantiate " + getClass().getSimpleName(), e);
        }
    }

    @Bean
    public GroovyShell getConfiguredShell() {
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.addCompilationCustomizers(new SandboxTransformer());
        return new GroovyShell(compilerConfiguration);
    }

    @Bean
    public CompilerConfiguration getCompilerConfiguration() {
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.addCompilationCustomizers(new SandboxTransformer());
        return compilerConfiguration;
    }

    @Bean
    public GroovyWhitelistInterceptor getGroovyWhitelistInterceptor() {
        return GroovyWhitelistInterceptor.createFromConfiguration(whitelistConfiguration);
    }

}
