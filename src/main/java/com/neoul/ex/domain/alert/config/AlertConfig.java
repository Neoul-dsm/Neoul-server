package com.neoul.ex.domain.alert.config;

import com.neoul.ex.domain.alert.dto.DrowningAlertResponse;
import com.neoul.ex.domain.alert.dto.MarineLifeAlertResponse;
import com.neoul.ex.domain.alert.service.DrowningAlertService;
import com.neoul.ex.domain.alert.service.MarineLifeAlertService;
import com.neoul.ex.domain.alert.service.PollingAlertStream;
import com.neoul.ex.global.handler.response.SuccessCode;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AlertConfig {
    @Bean
    public ThreadPoolTaskScheduler drowningAlertScheduler() {
        return scheduler("drowning-alert-");
    }

    @Bean
    public ThreadPoolTaskScheduler marineLifeAlertScheduler() {
        return scheduler("marine-life-alert-");
    }

    @Bean
    public PollingAlertStream<DrowningAlertResponse> drowningAlertStream(
            DrowningAlertService alerts, @Qualifier("drowningAlertScheduler") TaskScheduler scheduler, Clock clock,
            @Value("${alerts.drowning.poll-interval:PT1S}") Duration pollInterval,
            @Value("${alerts.drowning.stream-timeout:PT30M}") Duration streamTimeout) {
        return new PollingAlertStream<>(alerts, scheduler, clock, pollInterval, streamTimeout,
                SuccessCode.DROWNING_ALERT_STREAM_CONNECTED, SuccessCode.DROWNING_DETECTED, "person-detected");
    }

    @Bean
    public PollingAlertStream<MarineLifeAlertResponse> marineLifeAlertStream(
            MarineLifeAlertService alerts, @Qualifier("marineLifeAlertScheduler") TaskScheduler scheduler, Clock clock,
            @Value("${alerts.marine-life.poll-interval:PT1S}") Duration pollInterval,
            @Value("${alerts.marine-life.stream-timeout:PT30M}") Duration streamTimeout) {
        return new PollingAlertStream<>(alerts, scheduler, clock, pollInterval, streamTimeout,
                SuccessCode.MARINE_LIFE_ALERT_STREAM_CONNECTED, SuccessCode.MARINE_LIFE_DETECTED, "marine-life-detected");
    }

    private ThreadPoolTaskScheduler scheduler(String threadNamePrefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
