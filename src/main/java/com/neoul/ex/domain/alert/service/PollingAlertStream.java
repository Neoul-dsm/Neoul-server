package com.neoul.ex.domain.alert.service;

import com.neoul.ex.domain.alert.dto.AlertNotification;
import com.neoul.ex.global.security.AccessTokenPrincipal;
import com.neoul.ex.global.handler.response.Message;
import com.neoul.ex.global.handler.response.SuccessCode;
import com.neoul.ex.global.exception.BusinessException;
import com.neoul.ex.global.exception.ErrorCode;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class PollingAlertStream<T extends AlertNotification> {
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final int MAX_CONNECTIONS_PER_USER = 3;
    private static final int MAX_CONNECTIONS = 1000;
    private final Map<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final AlertSource<T> alerts;
    private final SuccessCode connectedCode;
    private final SuccessCode notificationCode;
    private final String eventName;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final Duration pollInterval;
    private final Duration streamTimeout;

    public PollingAlertStream(AlertSource<T> alerts, TaskScheduler scheduler, Clock clock,
            Duration pollInterval, Duration streamTimeout, SuccessCode connectedCode,
            SuccessCode notificationCode, String eventName) {
        this.connectedCode = connectedCode;
        this.notificationCode = notificationCode;
        this.eventName = eventName;
        if (pollInterval.toMillis() < 1 || streamTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("Alert intervals must be positive");
        }
        this.alerts = alerts;
        this.scheduler = scheduler;
        this.clock = clock;
        this.pollInterval = pollInterval;
        this.streamTimeout = streamTimeout;
    }

    public SseEmitter subscribe(AccessTokenPrincipal principal, String lastEventId) {
        Long beachId = alerts.authorize(principal);
        long cursor = alerts.validateCursor(beachId, lastEventId);
        var subscription = new Subscription(principal, beachId, cursor);
        synchronized (subscriptions) {
            long userConnections = subscriptions.values().stream()
                    .filter(current -> current.principal.userId().equals(principal.userId())).count();
            if (userConnections >= MAX_CONNECTIONS_PER_USER || subscriptions.size() >= MAX_CONNECTIONS) {
                throw new BusinessException(ErrorCode.TOO_MANY_ALERT_CONNECTIONS);
            }
            subscriptions.put(subscription.id, subscription);
        }
        subscription.emitter.onCompletion(subscription::close);
        subscription.emitter.onTimeout(subscription::close);
        subscription.emitter.onError(error -> subscription.close());
        try {
            subscription.emitter.send(SseEmitter.event().name("connected").reconnectTime(3000)
                    .data(Message.success(connectedCode, null), MediaType.APPLICATION_JSON));
            subscription.setTask(scheduler.scheduleWithFixedDelay(subscription::poll,
                    clock.instant().plus(pollInterval), pollInterval));
            return subscription.emitter;
        } catch (IOException | RuntimeException exception) {
            subscription.close();
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @PreDestroy
    public void closeAll() {
        subscriptions.values().forEach(Subscription::close);
    }

    private final class Subscription {
        private final UUID id = UUID.randomUUID();
        private final AccessTokenPrincipal principal;
        private final Long beachId;
        private final SseEmitter emitter = new SseEmitter(streamTimeout.toMillis());
        private long cursor;
        private Instant lastSentAt = clock.instant();
        private ScheduledFuture<?> task;
        private boolean closed;

        private Subscription(AccessTokenPrincipal principal, Long beachId, long cursor) {
            this.principal = principal;
            this.beachId = beachId;
            this.cursor = cursor;
        }

        private synchronized void setTask(ScheduledFuture<?> task) {
            this.task = task;
            if (closed) task.cancel(false);
        }

        private synchronized void poll() {
            if (closed) return;
            try {
                var notifications = alerts.getNext(principal, beachId, cursor);
                for (var notification : notifications) {
                    emitter.send(SseEmitter.event().id(notification.detectionId().toString()).name(eventName)
                            .data(Message.success(notificationCode, notification), MediaType.APPLICATION_JSON));
                    cursor = notification.detectionId();
                    lastSentAt = clock.instant();
                }
                if (!clock.instant().isBefore(lastSentAt.plus(HEARTBEAT_INTERVAL))) {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                    lastSentAt = clock.instant();
                }
            } catch (BusinessException | IOException exception) {
                close();
            } catch (RuntimeException exception) {
                log.error("Alert stream failed for event {}", eventName, exception);
                close();
            }
        }

        private synchronized void close() {
            if (closed) return;
            closed = true;
            subscriptions.remove(id);
            if (task != null) task.cancel(false);
            emitter.complete();
        }
    }
}
