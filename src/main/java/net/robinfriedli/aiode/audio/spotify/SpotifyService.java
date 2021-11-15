package net.robinfriedli.aiode.audio.spotify;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.ParseException;

import com.google.common.collect.Lists;
import com.neovisionaries.i18n.CountryCode;
import net.robinfriedli.aiode.Aiode;
import net.robinfriedli.aiode.concurrent.ThreadContext;
import net.robinfriedli.aiode.function.SpotifyInvoker;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Episode;
import se.michaelthelin.spotify.model_objects.specification.EpisodeSimplified;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.ShowSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import se.michaelthelin.spotify.requests.data.AbstractDataPagingRequest;
import se.michaelthelin.spotify.requests.data.AbstractDataRequest;

import static net.robinfriedli.aiode.util.SearchEngine.*;

/**
 * Service to request data from the Spotify API. Be sure to call these methods with the appropriate Spotify credentials,
 * see {@link SpotifyInvoker}. If you use these methods in a command you can use the runWithLogin or runWithCredentials method
 * of the AbstractCommand
 */
public class SpotifyService {

    private final SpotifyApi spotifyApi;

    public SpotifyService(SpotifyApi spotifyApi) {
        this.spotifyApi = spotifyApi;
    }

    public Track getTrack(String id) throws IOException, SpotifyWebApiException, ParseException {
        return spotifyApi.getTrack(id).market(getCurrentMarket()).build().execute();
    }

    /**
     * Wrapper around {@link SpotifyApi#getSeveralTracks(String...)} that uses the current market and performs several
     * requests if the length of the provided array exceeds 50 automatically.
     *
     * @param ids Spotify track ids to lookup
     */
    public List<Track> getSeveralTrack(String... ids) throws ParseException, SpotifyWebApiException, IOException {
        List<Track> tracks = Lists.newArrayList();
        List<List<String>> batches = Lists.partition(Arrays.asList(ids), 50);
        for (List<String> batch : batches) {
            Track[] result = spotifyApi.getSeveralTracks(batch.toArray(new String[0])).market(getCurrentMarket()).build().execute();
            tracks.addAll(Arrays.asList(result));
        }

        return tracks;
    }

    public Episode getEpisode(String id) throws IOException, SpotifyWebApiException, ParseException {
        return spotifyApi.getEpisode(id).market(getCurrentMarket()).build().execute();
    }

    /**
     * Wrapper around {@link SpotifyApi#getSeveralEpisodes(String...)} that uses the current market and performs several
     * requests if the length of the provided array exceeds 50 automatically.
     *
     * @param ids Spotify track ids to lookup
     */
    public List<Episode> getSeveralEpisodes(String... ids) throws ParseException, SpotifyWebApiException, IOException {
        List<Episode> tracks = Lists.newArrayList();
        List<List<String>> batches = Lists.partition(Arrays.asList(ids), 50);
        for (List<String> batch : batches) {
            Episode[] result = spotifyApi.getSeveralEpisodes(batch.toArray(new String[0])).market(getCurrentMarket()).build().execute();
            tracks.addAll(Arrays.asList(result));
        }

        return tracks;
    }

    public Artist getArtist(String id) throws IOException, SpotifyWebApiException, ParseException {
        return spotifyApi.getArtist(id).build().execute();
    }

    public List<Track> searchTrack(String searchTerm) throws IOException, SpotifyWebApiException, ParseException {
        return searchTrack(searchTerm, false);
    }

    public List<Track> searchTrack(String searchTerm, int limit) throws IOException, SpotifyWebApiException, ParseException {
        return searchTrack(searchTerm, false, limit);
    }

    public List<Track> searchTrack(String searchTerm, boolean limitToLibrary) throws IOException, SpotifyWebApiException, ParseException {
        return searchTrack(searchTerm, limitToLibrary, 20);
    }

    public List<Track> searchTrack(String searchTerm, boolean limitToLibrary, int limit) throws IOException, SpotifyWebApiException, ParseException {
        LibraryCheckFunction<Track> libraryCheckFunction = getLibraryCheckFunction(Track::getId, spotifyApi::checkUsersSavedTracks);
        return searchItem(limitToLibrary, limit, () -> spotifyApi.searchTracks(searchTerm).market(getCurrentMarket()), libraryCheckFunction);
    }

    public Playlist getPlaylist(String id) throws IOException, SpotifyWebApiException, ParseException {
        return spotifyApi.getPlaylist(id).market(getCurrentMarket()).build().execute();
    }

    public List<PlaylistSimplified> searchPlaylist(String searchTerm) throws IOException, SpotifyWebApiException, ParseException {
        return searchPlaylist(searchTerm, 20);
    }

    public List<PlaylistSimplified> searchPlaylist(String searchTerm, int limit) throws IOException, SpotifyWebApiException, ParseException {
        PlaylistSimplified[] results = spotifyApi.searchPlaylists(searchTerm).market(getCurrentMarket()).limit(limit).build().execute().getItems();
        return getBestLevenshteinMatches(results, searchTerm.toLowerCase(), playlist -> playlist.getName().toLowerCase());
    }

    public List<PlaylistSimplified> searchOwnPlaylist(String searchTerm) throws IOException, SpotifyWebApiException, ParseException {
        return searchOwnPlaylist(searchTerm, 20);
    }

    public List<PlaylistSimplified> searchOwnPlaylist(String searchTerm, int absoluteLimit) throws IOException, SpotifyWebApiException, ParseException {
        List<PlaylistSimplified> playlists = Lists.newArrayList();
        int limit = Math.min(absoluteLimit, 50);
        int offset = 0;
        String nextPage;
        do {
            Paging<PlaylistSimplified> paging = spotifyApi.getListOfCurrentUsersPlaylists().offset(offset).limit(limit).build().execute();
            playlists.addAll(Arrays.asList(paging.getItems()));
            offset = offset + limit;
            nextPage = paging.getNext();
        } while (nextPage != null);
        List<PlaylistSimplified> bestMatches = getBestLevenshteinMatches(playlists, searchTerm, PlaylistSimplified::getName);
        return bestMatches.size() <= absoluteLimit ? bestMatches : bestMatches.subList(0, absoluteLimit);
    }

    public List<SpotifyTrack> getPlaylistTracks(String playlistId) throws IOException, SpotifyWebApiException, ParseException {
        return getItemsOf(() -> spotifyApi.getPlaylistsItems(playlistId).market(getCurrentMarket()), (results, batch) -> results.addAll(Arrays.stream(batch)
            .filter(Objects::nonNull)
            .map(PlaylistTrack::getTrack)
            .filter(Objects::nonNull)
            .map(SpotifyTrack::wrap)
            .collect(Collectors.toList())
        ));
    }

    public List<SpotifyTrack> getPlaylistTracks(PlaylistSimplified playlistSimplified) throws IOException, SpotifyWebApiException, ParseException {
        return getPlaylistTracks(playlistSimplified.getId());
    }

    public List<AlbumSimplified> searchAlbum(String searchTerm) throws IOException, SpotifyWebApiException, ParseException {
        return searchAlbum(searchTerm, false);
    }

    public List<AlbumSimplified> searchAlbum(String searchTerm, int limit) throws IOException, SpotifyWebApiException, ParseException {
        return searchAlbum(searchTerm, false, limit);
    }

    public List<AlbumSimplified> searchAlbum(String searchTerm, boolean limitToLibrary) throws IOException, SpotifyWebApiException, ParseException {
        return searchAlbum(searchTerm, limitToLibrary, 20);
    }

    public List<AlbumSimplified> searchAlbum(String searchTerm, boolean limitToLibrary, int limit) throws IOException, SpotifyWebApiException, ParseException {
        LibraryCheckFunction<AlbumSimplified> libraryCheckFunction = getLibraryCheckFunction(AlbumSimplified::getId, spotifyApi::checkUsersSavedAlbums);
        return searchItem(limitToLibrary, limit, () -> spotifyApi.searchAlbums(searchTerm).market(getCurrentMarket()), libraryCheckFunction);
    }

    public List<Track> getAlbumTracks(String albumId) throws IOException, SpotifyWebApiException, ParseException {
        ResultHandler<Track, TrackSimplified> resultHandler = getResultHandler(TrackSimplified::getId, ids -> spotifyApi.getSeveralTracks(ids).market(getCurrentMarket()));
        return getItemsOf(() -> spotifyApi.getAlbumsTracks(albumId).market(getCurrentMarket()), resultHandler);
    }

    public List<Track> getAlbumTracks(AlbumSimplified albumSimplified) throws IOException, SpotifyWebApiException, ParseException {
        return getAlbumTracks(albumSimplified.getId());
    }

    public List<ShowSimplified> searchShow(String searchTerm) throws IOException, SpotifyWebApiException, ParseException {
        return searchShow(searchTerm, false, 20);
    }

    public List<ShowSimplified> searchShow(String searchTerm, int limit) throws IOException, SpotifyWebApiException, ParseException {
        return searchShow(searchTerm, false, limit);
    }

    public List<ShowSimplified> searchShow(String searchTerm, boolean limitToLibrary) throws IOException, SpotifyWebApiException, ParseException {
        return searchShow(searchTerm, limitToLibrary, 20);
    }

    public List<ShowSimplified> searchShow(String searchTerm, boolean limitToLibrary, int limit) throws IOException, SpotifyWebApiException, ParseException {
        LibraryCheckFunction<ShowSimplified> libraryCheckFunction = getLibraryCheckFunction(ShowSimplified::getId, spotifyApi::checkUsersSavedShows);
        return searchItem(limitToLibrary, limit, () -> spotifyApi.searchShows(searchTerm).market(getCurrentMarket()), libraryCheckFunction);
    }

    public List<Episode> searchEpisode(String searchTerm) throws IOException, SpotifyWebApiException, ParseException {
        return searchEpisode(searchTerm, false, 20);
    }

    public List<Episode> searchEpisode(String searchTerm, int limit) throws IOException, SpotifyWebApiException, ParseException {
        return searchEpisode(searchTerm, false, limit);
    }

    public List<Episode> searchEpisode(String searchTerm, boolean limitToLibrary) throws IOException, SpotifyWebApiException, ParseException {
        return searchEpisode(searchTerm, limitToLibrary, 20);
    }

    public List<Episode> searchEpisode(String searchTerm, boolean limitToLibrary, int limit) throws IOException, SpotifyWebApiException, ParseException {
        List<EpisodeSimplified> searchResultItems = searchItem(limitToLibrary, limit, () -> spotifyApi.searchEpisodes(searchTerm).market(getCurrentMarket()), batch -> {
            // this implements checking whether the episode is in the current user's library by checking whether its show is
            //
            // It is vital that the resulting Boolean[] matches the provided batch of EpisodeSimplified[] in size and indexing,
            // yet it is theoretically conceivable that getSeveralEpisodes returns null values when converting from
            // EpisodeSimplified to Episode, which is required to get the show. Therefore this implementation maps the
            // boolean value at the same index as the null-filtered list of episode ids, which might have a different
            // size, and finally iterates over the original array of EpisodeSimplified[] and retrieves the boolean value
            // for its id and maps false if absent.
            String[] ids = Arrays.stream(batch).map(EpisodeSimplified::getId).toArray(String[]::new);
            Episode[] episodes = spotifyApi.getSeveralEpisodes(ids).market(getCurrentMarket()).build().execute();

            List<String> episodeIds = Lists.newArrayList();
            List<String> showIds = Lists.newArrayList();
            for (Episode episode : episodes) {
                if (episode != null) {
                    episodeIds.add(episode.getId());
                    showIds.add(episode.getShow().getId());
                }
            }

            Boolean[] isInLibrary = spotifyApi.checkUsersSavedShows(showIds.toArray(new String[0])).build().execute();
            Map<String, Boolean> episodeInLibraryMap = new HashMap<>();

            if (isInLibrary.length != episodeIds.size()) {
                throw new IllegalStateException("checkUsersSavedShows check did not return exactly one boolean for each episode ID");
            }

            for (int i = 0; i < isInLibrary.length; i++) {
                episodeInLibraryMap.put(episodeIds.get(i), isInLibrary[i]);
            }

            return Arrays.stream(batch)
                .map(episode -> Objects.requireNonNullElse(episodeInLibraryMap.get(episode.getId()), false))
                .toArray(Boolean[]::new);
        });

        String[] resultIds = searchResultItems.stream().filter(Objects::nonNull).map(EpisodeSimplified::getId).toArray(String[]::new);
        return getSeveralEpisodes(resultIds);
    }

    public List<Episode> getShowEpisodes(String showId) throws ParseException, SpotifyWebApiException, IOException {
        ResultHandler<Episode, EpisodeSimplified> resultHandler = getResultHandler(EpisodeSimplified::getId, ids -> spotifyApi.getSeveralEpisodes(ids).market(getCurrentMarket()));
        return getItemsOf(() -> spotifyApi.getShowEpisodes(showId).market(getCurrentMarket()), resultHandler);
    }

    public SpotifyApi getSpotifyApi() {
        return spotifyApi;
    }

    private <T, R extends AbstractDataPagingRequest.Builder<T, ?>> List<T> searchItem(boolean limitToLibrary,
                                                                                      int limit,
                                                                                      Supplier<R> requestSupplier,
                                                                                      LibraryCheckFunction<T> libraryCheckFunction) throws IOException, SpotifyWebApiException, ParseException {
        List<T> results = Lists.newArrayList();

        if (limit <= 50) {
            T[] items = requestSupplier.get().limit(limit).build().execute().getItems();
            Boolean[] isInLibrary = limitToLibrary ? libraryCheckFunction.check(items) : null;
            for (int i = 0; i < items.length; i++) {
                if (results.size() < limit && (!limitToLibrary || isInLibrary[i])) {
                    results.add(items[i]);
                }
            }
        } else {
            String next;
            int i = 0;
            do {
                Paging<T> paging = requestSupplier.get().limit(50).offset(i * 50).build().execute();
                next = paging.getNext();
                T[] items = paging.getItems();

                if (items.length == 0) {
                    break;
                }

                Boolean[] isInLibrary = limitToLibrary ? libraryCheckFunction.check(items) : null;
                for (int j = 0; j < items.length; j++) {
                    boolean itemIsInLibrary = !limitToLibrary || isInLibrary[j];
                    if (itemIsInLibrary) {
                        if (results.size() < limit) {
                            results.add(items[j]);
                        } else {
                            return results;
                        }
                    }
                }

                i++;
            } while (next != null && i < 10 && results.size() < limit);
        }

        return results;
    }

    private <T, E, R extends AbstractDataPagingRequest.Builder<E, ?>> List<T> getItemsOf(Supplier<R> requestSupplier,
                                                                                         ResultHandler<T, E> resultHandler) throws ParseException, SpotifyWebApiException, IOException {
        List<T> results = Lists.newArrayList();
        int limit = 50;
        int offset = 0;
        String nextPage;
        do {
            Paging<E> paging = requestSupplier.get().offset(offset).limit(limit).build().execute();
            E[] items = paging.getItems();
            resultHandler.apply(results, items);
            offset = offset + limit;
            nextPage = paging.getNext();
        } while (nextPage != null);
        return results;
    }

    private <S, T extends IPlaylistItem, R extends AbstractDataRequest.Builder<T[], ?>> ResultHandler<T, S> getResultHandler(Function<S, String> idExtractor, Function<String[], R> requestProducer) {
        return (results, batch) -> {
            String[] ids = Arrays.stream(batch).filter(Objects::nonNull).map(idExtractor).toArray(String[]::new);
            T[] result = requestProducer.apply(ids).build().execute();
            results.addAll(Arrays.asList(result));
        };
    }

    private <T> LibraryCheckFunction<T> getLibraryCheckFunction(Function<T, String> idExtractor,
                                                                Function<String[], ? extends AbstractDataRequest.Builder<Boolean[], ?>> libraryCheckRequestProducer) {
        return batch -> {
            String[] ids = Arrays.stream(batch).map(idExtractor).toArray(String[]::new);
            return libraryCheckRequestProducer.apply(ids).build().execute();
        };
    }

    private CountryCode getCurrentMarket() {
        SpotifyContext spotifyContext = ThreadContext.Current.get().get(SpotifyContext.class);
        if (spotifyContext != null) {
            CountryCode market = spotifyContext.getMarket();
            if (market != null) {
                return market;
            }
        }

        return CountryCode.valueOf(Aiode.get().getSpotifyComponent().getDefaultMarket());
    }

    /**
     * Function that adds a batch of results to the total result list, possibly doing some conversions. E.g. taking a
     * batch result of {@link TrackSimplified}, requesting their full {@link Track} objects and then adding them to the
     * result list.
     *
     * @param <T> the target type after the conversion
     * @param <E> type of Spotify entity
     */
    @FunctionalInterface
    private interface ResultHandler<T, E> {

        void apply(List<T> results, E[] batch) throws IOException, SpotifyWebApiException, ParseException;

    }

    /**
     * Function responsible for returning a Boolean[] that matches the given T[] array where each boolean value in the
     * returned Boolean[] provides info whether the item in T[] at the same index is in the current user's library.
     *
     * @param <T> type of Spotify entity
     */
    @FunctionalInterface
    private interface LibraryCheckFunction<T> {

        Boolean[] check(T[] batch) throws IOException, SpotifyWebApiException, ParseException;

    }

}
