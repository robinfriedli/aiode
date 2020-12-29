package net.robinfriedli.botify.boot.configurations;

import java.io.IOException;
import java.util.HashMap;

import groovy.lang.GroovyShell;
import groovy.transform.CompileStatic;
import groovy.transform.ConditionalInterrupt;
import groovy.transform.ThreadInterrupt;
import net.robinfriedli.botify.scripting.GroovyInvocationCountCustomizer;
import net.robinfriedli.botify.scripting.GroovyWhitelistManager;
import net.robinfriedli.jxp.api.JxpBackend;
import net.robinfriedli.jxp.persist.Context;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import static java.util.Collections.*;

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
        ASTTransformationCustomizer compileStaticCustomizer = new ASTTransformationCustomizer(
            singletonMap("extensions", singletonList("net.robinfriedli.botify.scripting.TypeCheckingExtension")),
            CompileStatic.class
        );
        ASTTransformationCustomizer globalInvocationCounterCustomizer = new ASTTransformationCustomizer(
            singletonMap("value", GroovyInvocationCountCustomizer.GLOBAL_COUNT_INCREMENTATION_CLOSURE),
            ConditionalInterrupt.class
        );

        ImportCustomizer importCustomizer = getImportCustomizer();

        GroovyWhitelistManager groovyWhitelistManager = getGroovyWhitelistManager();
        GroovyInvocationCountCustomizer groovyInvocationCountCustomizer = new GroovyInvocationCountCustomizer(groovyWhitelistManager);
        compilerConfiguration.addCompilationCustomizers(
            compileStaticCustomizer,
            importCustomizer,
            threadInterruptCustomizer,
            groovyInvocationCountCustomizer,
            globalInvocationCounterCustomizer
        );

        return compilerConfiguration;
    }

    @Bean(name = "privileged_compiler_configuration", autowireCandidate = false)
    public CompilerConfiguration getPrivilegedCompilerConfiguration() {
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();

        ASTTransformationCustomizer threadInterruptCustomizer = new ASTTransformationCustomizer(new HashMap<>(), ThreadInterrupt.class);
        ImportCustomizer importCustomizer = getImportCustomizer();

        compilerConfiguration.addCompilationCustomizers(threadInterruptCustomizer, importCustomizer);

        return compilerConfiguration;
    }

    @Bean
    public GroovyWhitelistManager getGroovyWhitelistManager() {
        return GroovyWhitelistManager.createFromConfiguration(whitelistConfiguration);
    }

    private ImportCustomizer getImportCustomizer() {
        ImportCustomizer importCustomizer = new ImportCustomizer();
        importCustomizer.addImports("net.dv8tion.jda.api.EmbedBuilder", "java.util.stream.Collectors");
        return importCustomizer;
    }

}
