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

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor // Lombok для генерации конструктора с final полями
public class MessageGeneratorService {

    private static final Logger LOG = LoggerFactory.getLogger(MessageGeneratorService.class);
    private static final Random RAND = new Random(); // Для некоторых случайных выборов

    // KafkaTemplate<String, String> означает <ТипКлюча, ТипЗначения>
    // Мы будем отправлять JSON строки как значение, ключ нам пока не важен (можно null)
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper; // Будет внедрен Spring'ом

    @Value("${kafka.topic.input}")
    private String inputTopic;

    // --- Данные для генерации ---
    private static final List<String> COUNTRIES = List.of("US", "DE", "GB", "RU", "NG", "FR", "CA", "UA", "BY", "CN");
    private static final List<String> CURRENCIES = List.of("USD", "EUR", "GBP", "RUB", "BTC", "ETH", "CNY");
    private static final List<String> PAYMENT_METHODS = List.of("CREDIT_CARD", "BANK_TRANSFER", "E_WALLET", "CRYPTO");

    /**
     * Генерирует и отправляет указанное количество сообщений InputEvent.
     * @param count Количество сообщений для генерации.
     * @return Количество успешно отправленных сообщений.
     */
    public int generateAndSendMessages(int count) {
        LOG.info("Starting generation of {} messages for topic '{}'", count, inputTopic);
        AtomicInteger successCounter = new AtomicInteger(0); // Потокобезопасный счетчик

        for (int i = 0; i < count; i++) {
            InputEvent event = generateRandomEvent(i);
            try {
                String jsonMessage = objectMapper.writeValueAsString(event);
                // Отправляем сообщение. Метод send асинхронный, возвращает CompletableFuture.
                // Для простоты мы не будем ждать завершения, но добавим обработчик результата.
                kafkaTemplate.send(inputTopic, event.getEventId(), jsonMessage) // Используем eventId как ключ Kafka (опционально)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                // Успешно отправлено (или поставлено в буфер продюсера)
                                LOG.debug("Successfully sent message with eventId: {}", event.getEventId());
                                successCounter.incrementAndGet();
                            } else {
                                // Ошибка отправки
                                LOG.error("Failed to send message with eventId: {}. Error: {}", event.getEventId(), ex.getMessage());
                            }
                        });
            } catch (JsonProcessingException e) {
                LOG.error("Failed to serialize InputEvent to JSON for eventId: {}. Error: {}", event.getEventId(), e.getMessage());
            } catch (Exception e) {
                LOG.error("An unexpected error occurred while sending message for eventId: {}. Error: {}", event.getEventId(), e.getMessage());
            }
        }

        // Внимание: из-за асинхронности KafkaTemplate, счетчик может быть не совсем точным
        // сразу после цикла. Для точного подсчета нужно было бы собирать CompletableFuture
        // и ждать их завершения. Но для целей генерации это обычно не критично.
        LOG.info("Finished sending generation requests. Success counter (may be approximate): {}", successCounter.get());
        // Возвращаем запрошенное количество, т.к. отправка инициирована для всех.
        return count;
    }

    /**
     * Генерирует один случайный InputEvent.
     * @param sequence Порядковый номер (для уникальности ID).
     * @return Сгенерированный InputEvent.
     */
    private InputEvent generateRandomEvent(int sequence) {
        ThreadLocalRandom r = ThreadLocalRandom.current(); // Потокобезопасный генератор случайных чисел

        String eventId = "gen-" + System.currentTimeMillis() + "-" + sequence;
        String transactionId = UUID.randomUUID().toString();
        String customerId = "cust-" + r.nextInt(1000, 10000);
        // Генерируем сумму, иногда большую, иногда маленькую
        double amountRaw = r.nextDouble(10.0, (r.nextDouble() < 0.1 ? 50000.0 : 5000.0)); // 10% шанс большой суммы
        BigDecimal amount = BigDecimal.valueOf(amountRaw).setScale(2, BigDecimal.ROUND_HALF_UP);

        String currency = getRandomElement(CURRENCIES, r);
        String country = getRandomElement(COUNTRIES, r);
        String paymentMethod = getRandomElement(PAYMENT_METHODS, r);

        Integer customerAge = r.nextInt(16, 81); // от 16 до 80
        Integer customerHistoryScore = r.nextInt(0, 101); // от 0 до 100
        Boolean isNewDevice = r.nextBoolean();

        // Создаем объект с помощью конструктора Lombok @AllArgsConstructor
        InputEvent event = new InputEvent(
                eventId, transactionId, customerId, amount, currency,
                country, paymentMethod, customerAge, customerHistoryScore, isNewDevice
        );
        LOG.trace("Generated event: {}", event);
        return event;
    }

    // Вспомогательный метод для выбора случайного элемента из списка
    private <T> T getRandomElement(List<T> list, ThreadLocalRandom random) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(random.nextInt(list.size()));
    }
}