package net.robinfriedli.aiode.persist.customchange;

import net.robinfriedli.aiode.audio.spotify.SpotifyTrackKind;

public class SpotifyItemKindInitialValues extends InsertEnumLookupValuesChange<SpotifyTrackKind> {

    @Override
    protected SpotifyTrackKind[] getValues() {
        return SpotifyTrackKind.values();
    }

    @Override
    protected String getTableName() {
        return "spotify_item_kind";
    }
}
