package dms.test3.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dms.test3.core.DecisionResult; // Наш POJO с результатом
import dms.test3.entity.DecisionResultEntity;
import dms.test3.repository.DecisionResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload; // Для получения данных из сообщения
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Для управления транзакциями БД

@Service
public class DecisionPersistenceService {

    private static final Logger LOG = LoggerFactory.getLogger(DecisionPersistenceService.class);

    private final DecisionResultRepository repository;
    private final ObjectMapper objectMapper; // Используем тот же настроенный ObjectMapper

    @Autowired
    public DecisionPersistenceService(DecisionResultRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // Указываем топик, из которого читаем, и ID группы консьюмера
    // Важно: group ID должен быть СВОЙ, ОТЛИЧНЫЙ от Flink consumer group ID,
    // чтобы и Flink, и этот листенер получали копии сообщений.
    @KafkaListener(topics = "${kafka.topic.output}", groupId = "${kafka.group.id}-db-persister")
    @Transactional // Оборачиваем метод в транзакцию БД
    public void listenDecisionResults(@Payload String message) { // Получаем сообщение как строку
        LOG.debug("Received decision result message: {}", message);

        try {
            // 1. Десериализуем JSON строку в объект DecisionResult
            DecisionResult decisionResult = objectMapper.readValue(message, DecisionResult.class);

            // Проверяем, что десериализация прошла успешно и есть исходное событие
            if (decisionResult == null || decisionResult.getOriginalEvent() == null) {
                LOG.warn("Deserialized DecisionResult or its OriginalEvent is null. Skipping message: {}", message);
                return;
            }

            LOG.info("Processing decision for eventId: {}", decisionResult.getOriginalEvent().getEventId());

            // 2. Создаем JPA сущность DecisionResultEntity из DecisionResult
            // Передаем ObjectMapper для сериализации вложенного originalEvent в JSON поле сущности
            String originalEventJson = objectMapper.writeValueAsString(decisionResult.getOriginalEvent());
            DecisionResultEntity entity = new DecisionResultEntity(
                    decisionResult.getDecisionId(),
                    decisionResult.getRiskLevel(),
                    decisionResult.getOriginalEvent(),
                    originalEventJson // Передаем сериализованный JSON
            );

            // 3. Сохраняем сущность в базу данных с помощью репозитория
            DecisionResultEntity savedEntity = repository.save(entity);
            LOG.info("Decision result with ID {} (DB ID: {}) saved successfully.", savedEntity.getDecisionId(), savedEntity.getId());

        } catch (JsonProcessingException e) {
            LOG.error("Failed to deserialize DecisionResult JSON: {} - Error: {}", message, e.getMessage());
            // Здесь можно добавить логику отправки в Dead Letter Queue (DLQ) или другую обработку ошибок
        } catch (Exception e) {
            LOG.error("Failed to process or save decision result: {} - Error: {}", message, e.getMessage(), e);
            // Ошибка при сохранении в БД или другая проблема
            // Из-за @Transactional транзакция будет отменена
            // Можно добавить логику повторной попытки или DLQ
            // Важно: бросать исключение дальше, чтобы Spring Kafka мог обработать ошибку (например, повторить)
            // или обработать здесь и не бросать, если повторные попытки не нужны.
            // Для простоты пока просто логируем.
        }
    }
}
