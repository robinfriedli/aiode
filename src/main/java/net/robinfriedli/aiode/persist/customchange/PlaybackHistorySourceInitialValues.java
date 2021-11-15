package net.robinfriedli.aiode.persist.customchange;

import net.robinfriedli.aiode.audio.Playable;

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
