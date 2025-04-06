package dms.test3.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dms.test3.core.InputEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils; // Для проверки строк

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ManualMessageService {

    private static final Logger LOG = LoggerFactory.getLogger(ManualMessageService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.input}")
    private String inputTopic;

    /**
     * Отправляет предоставленное событие InputEvent в Kafka.
     * Генерирует eventId, если он отсутствует.
     *
     * @param event Событие для отправки.
     * @return CompletableFuture<Void> представляющий результат асинхронной отправки.
     *         Завершается успешно, если отправка инициирована, или с исключением при ошибке.
     * @throws JsonProcessingException если не удается сериализовать событие в JSON.
     * @throws IllegalArgumentException если событие null.
     */
    public CompletableFuture<Void> sendManualEvent(InputEvent event) throws JsonProcessingException {
        if (event == null) {
            throw new IllegalArgumentException("InputEvent cannot be null.");
        }

        // Генерируем eventId, если он пустой
        if (!StringUtils.hasText(event.getEventId())) {
            String generatedEventId = "manual-" + System.currentTimeMillis();
            LOG.warn("Received manual event without eventId, generated one: {}", generatedEventId);
            event.setEventId(generatedEventId);
        }

        LOG.info("Preparing to send manual event: eventId={}", event.getEventId());

        String jsonMessage = objectMapper.writeValueAsString(event);
        LOG.debug("Sending manual message to Kafka topic '{}': {}", inputTopic, jsonMessage);

        // Отправляем в Kafka, используем eventId как ключ
        // Возвращаем CompletableFuture, чтобы вызывающий мог (при желании) обработать результат
        return kafkaTemplate.send(inputTopic, event.getEventId(), jsonMessage)
                .thenAccept(result -> {
                    // Этот блок выполнится при успешной асинхронной отправке (или постановке в буфер)
                    LOG.info("Successfully sent manual message with eventId: {}", event.getEventId());
                })
                .exceptionally(ex -> {
                    // Этот блок выполнится при ошибке асинхронной отправки
                    LOG.error("Failed to send manual message with eventId {} asynchronously. Error: {}", event.getEventId(), ex.getMessage(), ex);
                    // Можно пробросить исключение дальше, обернув его, если нужно сигнализировать об ошибке
                    // throw new RuntimeException("Kafka send failed for eventId " + event.getEventId(), ex);
                    return null; // Обязательно что-то вернуть в exceptionally
                });
    }
}