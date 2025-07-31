package com.cdwater.ticketbox.dev;

import com.cdwater.ticketbox.event.domain.Event;
import com.cdwater.ticketbox.event.domain.EventRepository;
import com.cdwater.ticketbox.performance.domain.Performance;
import com.cdwater.ticketbox.performance.domain.PerformanceRepository;
import com.cdwater.ticketbox.performance.domain.PriceTier;
import com.cdwater.ticketbox.performance.domain.PriceTierRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@Profile("dev")
public class DemoDataSeeder implements ApplicationRunner {

    private final EventRepository events;
    private final PerformanceRepository performances;
    private final PriceTierRepository priceTiers;

    public DemoDataSeeder(EventRepository events,
                          PerformanceRepository performances,
                          PriceTierRepository priceTiers) {
        this.events = events;
        this.performances = performances;
        this.priceTiers = priceTiers;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (events.count() > 0) {
            return;
        }

        Event event = new Event(
                "城市之光 · 2026 巡回演唱会",
                "一场关于城市夜色与现场乐队的演出。整场约 120 分钟，适合和朋友一起看。",
                "上海",
                null);
        event.publish();
        events.save(event);

        Performance performance = performances.save(new Performance(
                event.getId(),
                Instant.parse("2026-12-20T11:30:00Z"),
                "梅赛德斯-奔驰文化中心",
                "浦东新区世博大道1200号",
                Instant.parse("2026-12-01T02:00:00Z")));

        priceTiers.save(new PriceTier(performance.getId(), "看台", new BigDecimal("280.00"), 200));
        priceTiers.save(new PriceTier(performance.getId(), "内场", new BigDecimal("680.00"), 100));
    }
}
