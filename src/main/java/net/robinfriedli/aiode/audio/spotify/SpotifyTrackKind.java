package net.robinfriedli.aiode.audio.spotify;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.ParseException;

import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;

public enum SpotifyTrackKind {

    TRACK {
        @Override
        public List<SpotifyTrack> loadSeveralItems(SpotifyService spotifyService, String[] ids) throws ParseException, SpotifyWebApiException, IOException {
            return spotifyService.getSeveralTrack(ids).stream().map(SpotifyTrack::wrapIfNotNull).collect(Collectors.toList());
        }

        @Override
        public SpotifyTrack loadSingleItem(SpotifyService spotifyService, String id) throws ParseException, SpotifyWebApiException, IOException {
            return SpotifyTrack.wrapIfNotNull(spotifyService.getTrack(id));
        }
    },
    EPISODE {
        @Override
        public List<SpotifyTrack> loadSeveralItems(SpotifyService spotifyService, String[] ids) throws ParseException, SpotifyWebApiException, IOException {
            return spotifyService.getSeveralEpisodes(ids).stream().map(SpotifyTrack::wrapIfNotNull).collect(Collectors.toList());
        }

        @Override
        public SpotifyTrack loadSingleItem(SpotifyService spotifyService, String id) throws ParseException, SpotifyWebApiException, IOException {
            return SpotifyTrack.wrapIfNotNull(spotifyService.getEpisode(id));
        }
    };

    public abstract List<SpotifyTrack> loadSeveralItems(SpotifyService spotifyService, String[] ids) throws ParseException, SpotifyWebApiException, IOException;

    public abstract SpotifyTrack loadSingleItem(SpotifyService spotifyService, String id) throws ParseException, SpotifyWebApiException, IOException;
}
