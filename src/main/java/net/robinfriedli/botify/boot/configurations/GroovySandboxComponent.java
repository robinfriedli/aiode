package net.robinfriedli.botify.boot.configurations;

import java.io.IOException;
import java.util.HashMap;

import groovy.lang.GroovyShell;
import groovy.transform.ThreadInterrupt;
import net.robinfriedli.botify.scripting.GroovyWhitelistInterceptor;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
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
            this.whitelistConfiguration = jxpBackend.createContext(whitelistResource.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Could not instantiate " + getClass().getSimpleName(), e);
        }
    }

    @Bean
    public GroovyShell getConfiguredShell() {
        return new GroovyShell(getCompilerConfiguration());
    }

    @Bean
    public CompilerConfiguration getCompilerConfiguration() {
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        ASTTransformationCustomizer threadInterruptCustomizer = new ASTTransformationCustomizer(new HashMap<>(), ThreadInterrupt.class);
        ImportCustomizer importCustomizer = new ImportCustomizer();
        importCustomizer.addImports("net.dv8tion.jda.api.EmbedBuilder", "java.util.stream.Collectors");
        compilerConfiguration.addCompilationCustomizers(new SandboxTransformer(), threadInterruptCustomizer, importCustomizer);
        return compilerConfiguration;
    }

    @Bean
    public GroovyWhitelistInterceptor getGroovyWhitelistInterceptor() {
        return GroovyWhitelistInterceptor.createFromConfiguration(whitelistConfiguration);
    }

}
