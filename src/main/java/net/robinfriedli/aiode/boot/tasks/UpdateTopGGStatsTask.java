package net.robinfriedli.aiode.boot.tasks;

import java.util.Objects;

import net.dv8tion.jda.api.JDA;
import net.robinfriedli.aiode.boot.StartupTask;
import net.robinfriedli.aiode.boot.configurations.TopGGComponent;
import net.robinfriedli.aiode.entities.xml.StartupTaskContribution;
import org.jetbrains.annotations.Nullable;

public class UpdateTopGGStatsTask implements StartupTask {

    private final StartupTaskContribution contribution;
    private final TopGGComponent topGGComponent;

    public UpdateTopGGStatsTask(StartupTaskContribution contribution, TopGGComponent topGGComponent) {
        this.contribution = contribution;
        this.topGGComponent = topGGComponent;
    }

    @Override
    public void perform(@Nullable JDA shard) throws Exception {
        topGGComponent.updateStatsForShard(Objects.requireNonNull(shard));
    }

    @Override
    public StartupTaskContribution getContribution() {
        return contribution;
    }
}
