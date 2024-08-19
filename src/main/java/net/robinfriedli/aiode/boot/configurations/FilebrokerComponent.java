package net.robinfriedli.aiode.boot.configurations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import net.robinfriedli.filebroker.FilebrokerApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilebrokerComponent {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${aiode.filebroker.api_base_url:#{null}}")
    private String apiBaseUrl;
    @Value("${aiode.filebroker.bot_user_name:#{null}}")
    private String botUserName;
    @Value("${aiode.filebroker.bot_user_password:#{null}}")
    private String botUserPassword;

    @Bean
    public FilebrokerApi getFilebrokerApi() {
        String apiBaseUrl = getApiBaseUrl();
        FilebrokerApi filebrokerApi = new FilebrokerApi(apiBaseUrl, null, null);

        if (!Strings.isNullOrEmpty(this.botUserName) && !Strings.isNullOrEmpty(this.botUserPassword)) {
            filebrokerApi
                .loginAsync(new FilebrokerApi.LoginRequest(botUserName, botUserPassword))
                .whenComplete((res, e) -> {
                    if (res != null) {
                        logger.info("Logged in to filebroker as user " + res.getUser().getUser_name());
                    }
                    if (e != null) {
                        logger.error("Failed to login to filebroker", e);
                    }
                });
        } else {
            logger.warn("No filebroker credentials set up, bot can only find public content. Set aiode.filebroker.bot_user_name and aiode.filebroker.bot_user_password.");
        }

        return filebrokerApi;
    }

    public String getApiBaseUrl() {
        String apiBaseUrl;
        if (Strings.isNullOrEmpty(this.apiBaseUrl)) {
            apiBaseUrl = FilebrokerApi.getBASE_URL();
        } else {
            apiBaseUrl = this.apiBaseUrl;
        }
        return apiBaseUrl;
    }

}
