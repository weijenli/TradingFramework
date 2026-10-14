package com.tradingframework.daily;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs a job once per trading day after the market closes (default 16:30 America/New_York),
 * skipping weekends and the configured holidays. The next run is recomputed after each run, so
 * daylight-saving changes are handled.
 */
public class MarketCloseScheduler implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(MarketCloseScheduler.class.getName());

    private final LocalTime runTime;
    private final ZoneId zone;
    private final Set<LocalDate> holidays;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "market-close-scheduler");
        t.setDaemon(false);
        return t;
    });

    public MarketCloseScheduler(LocalTime runTime, ZoneId zone, Set<LocalDate> holidays) {
        this.runTime = runTime;
        this.zone = zone;
        this.holidays = Set.copyOf(holidays);
    }

    public void start(Runnable job) {
        scheduleNext(job);
    }

    private void scheduleNext(Runnable job) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = nextRun(now);
        long delayMs = Duration.between(now, next).toMillis();
        LOG.log(System.Logger.Level.INFO, "Next daily scan at {0}", next);
        executor.schedule(() -> {
            try {
                job.run();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "Daily job failed", e);
            } finally {
                scheduleNext(job);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /** First trading day at {@code runTime} strictly after {@code now}. */
    public ZonedDateTime nextRun(ZonedDateTime now) {
        ZonedDateTime candidate = now.withZoneSameInstant(zone).with(runTime).withSecond(0).withNano(0);
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);
        while (!isTradingDay(candidate.toLocalDate())) candidate = candidate.plusDays(1);
        return candidate;
    }

    public boolean isTradingDay(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(d);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
