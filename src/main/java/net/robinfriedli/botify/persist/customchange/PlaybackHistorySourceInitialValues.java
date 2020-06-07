package net.robinfriedli.botify.persist.customchange;

import net.robinfriedli.botify.audio.Playable;

public class PlaybackHistorySourceInitialValues extends InsertEnumLookupValuesChange<Playable.Source> {

    @Override
    protected Playable.Source[] getValues() {
        return Playable.Source.values();
    }

    @Override
    protected String getTableName() {
        return "playback_history_source";
    }
}
