package net.robinfriedli.aiode.boot.configurations;

import java.io.IOException;
import java.security.GeneralSecurityException;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
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
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            return new YouTube.Builder(httpTransport, jsonFactory, httpRequest -> {
                // no-op
            }).setApplicationName("botify-youtube-search").build();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException("Exception while instantiating YouTube API");
        }
    }

}
