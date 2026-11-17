package com.reqwise.service.pipeline;

import com.reqwise.dto.PipelineProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(SseNotificationService.class);
    private static final Long DEFAULT_TIMEOUT = 30 * 60 * 1000L; // 30 minutes timeout for pipeline jobs

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID versionId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        emitters.computeIfAbsent(versionId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(versionId, emitter));
        emitter.onTimeout(() -> removeEmitter(versionId, emitter));
        emitter.onError(e -> removeEmitter(versionId, emitter));

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Subscribed to version " + versionId));
        } catch (IOException e) {
            removeEmitter(versionId, emitter);
        }

        return emitter;
    }

    public void emitProgress(PipelineProgressEvent event) {
        UUID versionId = event.versionId();
        List<SseEmitter> versionEmitters = emitters.get(versionId);
        if (versionEmitters == null || versionEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : versionEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(event));
            } catch (IOException e) {
                logger.debug("Client disconnected during SSE broadcast for version {}", versionId);
                removeEmitter(versionId, emitter);
            }
        }
    }

    public void complete(UUID versionId, String message) {
        PipelineProgressEvent event = PipelineProgressEvent.of(versionId, "COMPLETED", 100.0, message);
        emitProgress(event);

        List<SseEmitter> versionEmitters = emitters.remove(versionId);
        if (versionEmitters != null) {
            for (SseEmitter emitter : versionEmitters) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void fail(UUID versionId, String errorMessage) {
        PipelineProgressEvent event = PipelineProgressEvent.of(versionId, "FAILED", 0.0, errorMessage);
        emitProgress(event);

        List<SseEmitter> versionEmitters = emitters.remove(versionId);
        if (versionEmitters != null) {
            for (SseEmitter emitter : versionEmitters) {
                try {
                    emitter.completeWithError(new RuntimeException(errorMessage));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void removeEmitter(UUID versionId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(versionId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(versionId);
            }
        }
    }
}
