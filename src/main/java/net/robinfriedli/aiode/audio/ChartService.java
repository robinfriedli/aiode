package net.robinfriedli.aiode.audio;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.google.common.collect.Lists;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.robinfriedli.aiode.boot.configurations.HibernateComponent;
import net.robinfriedli.aiode.entities.Artist;
import net.robinfriedli.aiode.entities.GlobalArtistChart;
import net.robinfriedli.aiode.entities.GlobalTrackChart;
import net.robinfriedli.aiode.entities.PlaybackHistory;
import net.robinfriedli.aiode.entities.PlaybackHistorySource;
import net.robinfriedli.aiode.entities.SpotifyItemKind;
import net.robinfriedli.aiode.entities.UserPlaybackHistory;
import net.robinfriedli.aiode.persist.qb.QueryBuilderFactory;
import net.robinfriedli.aiode.persist.qb.builders.SelectQueryBuilder;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.stereotype.Component;

@Component
public class ChartService {

    private final HibernateComponent hibernateComponent;
    private final QueryBuilderFactory queryBuilderFactory;

    public ChartService(HibernateComponent hibernateComponent, QueryBuilderFactory queryBuilderFactory) {
        this.hibernateComponent = hibernateComponent;
        this.queryBuilderFactory = queryBuilderFactory;
    }

    public void refreshPersistentGlobalCharts() {
        hibernateComponent.consumeSession(session -> {
            session.createNativeMutationQuery("delete from global_artist_chart").executeUpdate();
            session.createNativeMutationQuery("delete from global_track_chart").executeUpdate();

            for (Object[] record : getGlobalTrackChart(session)) {
                GlobalTrackChart globalTrackChart = new GlobalTrackChart();
                globalTrackChart.setSource(session.get(PlaybackHistorySource.class, (long) record[0]));
                globalTrackChart.setTrackId((String) record[1]);
                globalTrackChart.setCount((long) record[2]);
                Long spotifyItemKindPk = (Long) record[3];
                if (spotifyItemKindPk != null) {
                    globalTrackChart.setSpotifyItemKind(session.get(SpotifyItemKind.class, spotifyItemKindPk));
                }
                globalTrackChart.setMonthly(false);
                session.persist(globalTrackChart);
            }

            for (Object[] record : getGlobalTrackMonthlyChart(session)) {
                GlobalTrackChart globalTrackChart = new GlobalTrackChart();
                globalTrackChart.setSource(session.get(PlaybackHistorySource.class, (long) record[0]));
                globalTrackChart.setTrackId((String) record[1]);
                globalTrackChart.setCount((long) record[2]);
                Long spotifyItemKindPk = (Long) record[3];
                if (spotifyItemKindPk != null) {
                    globalTrackChart.setSpotifyItemKind(session.get(SpotifyItemKind.class, spotifyItemKindPk));
                }
                globalTrackChart.setMonthly(true);
                session.persist(globalTrackChart);
            }

            for (Object[] record : getGlobalArtistChart(session)) {
                GlobalArtistChart globalArtistChart = new GlobalArtistChart();
                globalArtistChart.setArtist(session.get(Artist.class, record[0]));
                globalArtistChart.setCount((Long) record[1]);
                globalArtistChart.setMonthly(false);
                session.persist(globalArtistChart);
            }

            for (Object[] record : getGlobalArtistMonthlyChart(session)) {
                GlobalArtistChart globalArtistChart = new GlobalArtistChart();
                globalArtistChart.setArtist(session.get(Artist.class, record[0]));
                globalArtistChart.setCount((Long) record[1]);
                globalArtistChart.setMonthly(true);
                session.persist(globalArtistChart);
            }
        });
    }

    public SelectQueryBuilder<PlaybackHistory> getGlobalTrackChartQuery() {
        return queryBuilderFactory
            .select(PlaybackHistory.class,
                (from, cb) -> from.get("fkSource"),
                (from, cb) -> from.get("trackId"),
                (from, cb) -> cb.count(from.get("pk")),
                (from, cb) -> from.get("fkSpotifyItemKind"))
            .where((cb, root) -> cb.isNotNull(root.get("trackId")))
            .groupBySeveral((from, cb) -> Lists.newArrayList(from.get("trackId"), from.get("fkSource"), from.get("fkSpotifyItemKind")))
            .orderBy((from, cb) -> cb.desc(cb.count(from.get("pk"))));
    }

    public List<Object[]> getGlobalTrackChart(Session session) {
        return getGlobalTrackChartQuery().build(session).setMaxResults(10).getResultList();
    }

    public SelectQueryBuilder<GlobalTrackChart> getPersistentGlobalTrackChartQuery() {
        return queryBuilderFactory
            .select(GlobalTrackChart.class,
                (from, cb) -> from.get("fkSource"),
                (from, cb) -> from.get("trackId"),
                (from, cb) -> from.get("count"),
                (from, cb) -> from.get("fkSpotifyItemKind"))
            .where((cb, root) -> cb.not(root.get("isMonthly")))
            .orderBy((from, cb) -> cb.desc(from.get("count")));
    }

    public List<Object[]> getPersistentGlobalTrackChart(Session session) {
        return getPersistentGlobalTrackChartQuery().build(session).setMaxResults(10).getResultList();
    }

    public SelectQueryBuilder<PlaybackHistory> getGlobalTrackMonthlyChartQuery() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return getGlobalTrackChartQuery().fork().where((cb, root) -> cb.greaterThan(root.get("timestamp"), startOfMonth));
    }

    public List<Object[]> getGlobalTrackMonthlyChart(Session session) {
        return getGlobalTrackMonthlyChartQuery().build(session).setMaxResults(10).getResultList();
    }

    public SelectQueryBuilder<GlobalTrackChart> getPersistentGlobalTrackMonthlyChartQuery() {
        return queryBuilderFactory
            .select(GlobalTrackChart.class,
                (from, cb) -> from.get("fkSource"),
                (from, cb) -> from.get("trackId"),
                (from, cb) -> from.get("count"),
                (from, cb) -> from.get("fkSpotifyItemKind"))
            .where((cb, root) -> cb.isTrue(root.get("isMonthly")))
            .orderBy((from, cb) -> cb.desc(from.get("count")));
    }

    public List<Object[]> getPersistentGlobalTrackMonthlyChart(Session session) {
        return getPersistentGlobalTrackMonthlyChartQuery().build(session).setMaxResults(10).getResultList();
    }

    public SelectQueryBuilder<PlaybackHistory> getGuildTrackChartQuery(Guild guild) {
        return getGlobalTrackChartQuery().fork().where((cb, root) -> cb.equal(root.get("guildId"), guild.getId()));
    }

    public List<Object[]> getGuildTrackChart(Guild guild, Session session) {
        return getGuildTrackChartQuery(guild).build(session).setMaxResults(10).getResultList();
    }

    public SelectQueryBuilder<PlaybackHistory> getGuildTrackMonthlyChartQuery(Guild guild) {
        return getGlobalTrackMonthlyChartQuery().fork().where((cb, root) -> cb.equal(root.get("guildId"), guild.getId()));
    }

    public List<Object[]> getGuildTrackMonthlyChart(Guild guild, Session session) {
        return getGuildTrackMonthlyChartQuery(guild).build(session).setMaxResults(10).getResultList();
    }

    public SelectQueryBuilder<PlaybackHistory> getUserTrackChartQuery(User user, Session session) {
        return getGlobalTrackChartQuery().where((cb, root, subQueryFactory) -> root.get("pk").in(
            subQueryFactory
                .createUncorrelatedSubQuery(UserPlaybackHistory.class, "playbackHistoryPk", Long.class)
                .where((cb1, root1) -> cb1.equal(root1.get("userId"), user.getId()))
                .build(session)
        ));
    }

    public List<Object[]> getUserTrackChart(User user, Session session) {
        return getUserTrackChartQuery(user, session).build(session).setMaxResults(10).getResultList();
    }

    public SelectQueryBuilder<PlaybackHistory> getUserTrackMonthlyChartQuery(User user, Session session) {
        return getGlobalTrackMonthlyChartQuery().fork().where((cb, root, subQueryFactory) -> root.get("pk").in(
            subQueryFactory
                .createUncorrelatedSubQuery(UserPlaybackHistory.class, "playbackHistoryPk", Long.class)
                .where((cb1, root1) -> cb1.equal(root1.get("userId"), user.getId()))
                .build(session)
        ));
    }

    public List<Object[]> getUserTrackMonthlyChart(User user, Session session) {
        return getUserTrackMonthlyChartQuery(user, session).build(session).setMaxResults(10).getResultList();
    }

    public Query<Object[]> getGlobalArtistChartQuery(Session session) {
        return session.createNativeQuery("select artist_pk, count(*) as c " +
            "from playback_history_artist group by artist_pk order by c desc limit 5", Object[].class);
    }

    public List<Object[]> getGlobalArtistChart(Session session) {
        return getGlobalArtistChartQuery(session).getResultList();
    }

    public Query<Object[]> getPersistentGlobalArtistChartQuery(Session session) {
        return session.createNativeQuery("select artist_pk, count as c " +
            "from global_artist_chart where not is_monthly order by c desc limit 5", Object[].class);
    }

    public List<Object[]> getPersistentGlobalArtistChart(Session session) {
        return getPersistentGlobalArtistChartQuery(session).getResultList();
    }

    public Query<Object[]> getGlobalArtistMonthlyChartQuery(Session session) {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Date dateAtStartOfMonth = Date.valueOf(startOfMonth.toLocalDate());
        Query<Object[]> globalArtistMonthlyQuery = session.createNativeQuery("select artist_pk, count(*) as c " +
            "from playback_history_artist as p " +
            "where p.playback_history_pk in(select pk from playback_history where timestamp > ?) " +
            "group by artist_pk order by c desc limit 5", Object[].class);
        globalArtistMonthlyQuery.setParameter(1, dateAtStartOfMonth);
        return globalArtistMonthlyQuery;
    }

    public List<Object[]> getGlobalArtistMonthlyChart(Session session) {
        return getGlobalArtistMonthlyChartQuery(session).getResultList();
    }

    public Query<Object[]> getPersistentGlobalArtistMonthlyChartQuery(Session session) {
        return session.createNativeQuery("select artist_pk, count as c " +
            "from global_artist_chart " +
            "where is_monthly " +
            "order by c desc limit 5", Object[].class);
    }

    public List<Object[]> getPersistentGlobalArtistMonthlyChart(Session session) {
        return getPersistentGlobalArtistMonthlyChartQuery(session).getResultList();
    }

    public Query<Object[]> getGuildArtistChartQuery(Guild guild, Session session) {
        Query<Object[]> guildArtistQuery = session.createNativeQuery("select artist_pk, count(*) as c from " +
            "playback_history_artist as p where p.playback_history_pk in(select pk from playback_history where guild_id = ?) " +
            "group by artist_pk order by c desc limit 5", Object[].class);
        guildArtistQuery.setParameter(1, guild.getId());
        return guildArtistQuery;
    }

    public List<Object[]> getGuildArtistChart(Guild guild, Session session) {
        return getGuildArtistChartQuery(guild, session).getResultList();
    }

    public Query<Object[]> getGuildArtistMonthlyChartQuery(Guild guild, Session session) {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Date dateAtStartOfMonth = Date.valueOf(startOfMonth.toLocalDate());
        Query<Object[]> guildArtistMonthlyQuery = session.createNativeQuery("select artist_pk, count(*) as c " +
            "from playback_history_artist where playback_history_pk in(select pk from playback_history " +
            "where timestamp > ? and guild_id = ?) " +
            "group by artist_pk order by c desc limit 5", Object[].class);
        guildArtistMonthlyQuery.setParameter(1, dateAtStartOfMonth);
        guildArtistMonthlyQuery.setParameter(2, guild.getId());
        return guildArtistMonthlyQuery;
    }

    public List<Object[]> getGuildArtistMonthlyChart(Guild guild, Session session) {
        return getGuildArtistMonthlyChartQuery(guild, session).getResultList();
    }

    public Query<Object[]> getUserArtistChartQuery(User user, Session session) {
        Query<Object[]> userArtistQuery = session.createNativeQuery("select artist_pk, count(*) as c from " +
            "playback_history_artist as p where p.playback_history_pk in(select pk from playback_history where pk IN(SELECT playback_history_pk FROM user_playback_history WHERE user_id = ?)) " +
            "group by artist_pk order by c desc limit 5", Object[].class);
        userArtistQuery.setParameter(1, user.getId());
        return userArtistQuery;
    }

    public List<Object[]> getUserArtistChart(User user, Session session) {
        return getUserArtistChartQuery(user, session).getResultList();
    }

    public Query<Object[]> getUserArtistMonthlyChartQuery(User user, Session session) {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Date dateAtStartOfMonth = Date.valueOf(startOfMonth.toLocalDate());
        Query<Object[]> userArtistMonthlyQuery = session.createNativeQuery("select artist_pk, count(*) as c " +
            "from playback_history_artist where playback_history_pk in(select pk from playback_history " +
            "where timestamp > ? and pk IN(SELECT playback_history_pk FROM user_playback_history WHERE user_id = ?)) " +
            "group by artist_pk order by c desc limit 5", Object[].class);
        userArtistMonthlyQuery.setParameter(1, dateAtStartOfMonth);
        userArtistMonthlyQuery.setParameter(2, user.getId());
        return userArtistMonthlyQuery;
    }

    public List<Object[]> getUserArtistMonthlyChart(User user, Session session) {
        return getUserArtistMonthlyChartQuery(user, session).getResultList();
    }

}
