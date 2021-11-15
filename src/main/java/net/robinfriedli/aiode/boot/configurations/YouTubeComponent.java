package net.robinfriedli.aiode.boot.configurations;

import java.io.IOException;
import java.security.GeneralSecurityException;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class YouTubeComponent {

    @Bean
    public YouTube getYouTube() {
        try {
            // setup YouTube API
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JacksonFactory jacksonFactory = JacksonFactory.getDefaultInstance();
            return new YouTube.Builder(httpTransport, jacksonFactory, httpRequest -> {
                // no-op
            }).setApplicationName("botify-youtube-search").build();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Exception while instantiating YouTube API");
        }
    }

}
