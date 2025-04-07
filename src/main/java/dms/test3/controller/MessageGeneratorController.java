package dms.test3.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.benmanes.caffeine.cache.Cache;
import dms.test3.core.InputEvent;
import dms.test3.dto.InputEventDto;
import dms.test3.service.ManualMessageService;
import dms.test3.service.MessageGeneratorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api") // Базовый путь для всех эндпоинтов контроллера
@RequiredArgsConstructor
public class MessageGeneratorController {

    private static final Logger LOG = LoggerFactory.getLogger(MessageGeneratorController.class);
    private final MessageGeneratorService messageGeneratorService;
    private final Cache<String, Boolean> requestDebounceCache; // Внедряем кэш
    private final ManualMessageService manualMessageService;


    @GetMapping("/generate") // Обрабатывает GET запросы на /api/generate
    public ResponseEntity<String> generateMessages(
            // Принимаем параметр 'count' из запроса, по умолчанию 10
            @RequestParam(name = "count", defaultValue = "10") int count,
            HttpServletRequest request // Добавляем HttpServletRequest
    ) {
        LOG.info("Received request to generate {} messages.", count);

        // Создаем ключ на основе запроса (упрощенный пример)
        String requestKey = "GENERATE:" + request.getRequestURI() + "?" + request.getQueryString();
        // Можно добавить request.getRemoteAddr(), но будьте осторожны с прокси

        // Проверяем кэш
        if (requestDebounceCache.getIfPresent(requestKey) != null) {
            LOG.warn("Debounced duplicate request detected for key: {}", requestKey);
            // Возвращаем ОК, чтобы браузер не показывал ошибку, но ничего не делаем
            return ResponseEntity.ok("Request successfully processed (debounced duplicate).");
            // Или можно вернуть 429:
            // return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Duplicate request detected within debounce window.");
        }

        // Ключа нет - записываем его и выполняем действие
        requestDebounceCache.put(requestKey, Boolean.TRUE);
        LOG.info("Received request to generate {} messages.", count);

        if (count <= 0 || count > 10000) { // Простое ограничение
            LOG.warn("Invalid count requested: {}. Must be between 1 and 10000.", count);
            return ResponseEntity.badRequest().body("Count must be between 1 and 10000.");
        }

        try {
            int generatedCount = messageGeneratorService.generateAndSendMessages(count);
            String responseMessage = String.format("Generation of %d messages initiated successfully.", generatedCount);
            LOG.info(responseMessage);
            return ResponseEntity.ok(responseMessage);
        } catch (Exception e) {
            LOG.error("Error during message generation request.", e);
            return ResponseEntity.internalServerError().body("Failed to initiate message generation: " + e.getMessage());
        }
    }

    // --- ОБНОВЛЕННЫЙ Эндпоинт для ручной отправки ---
    @PostMapping("/send-manual")
    // Используем @Valid для активации валидации DTO
    // Принимаем InputEventDto
    public ResponseEntity<String> sendManualMessage(@Valid @RequestBody(required = true) InputEventDto eventDto) {
        // Если @RequestBody null или DTO не прошел валидацию (@Valid),
        // Spring выбросит исключение ДО вызова этого метода,
        // и стандартный обработчик вернет 400 Bad Request с деталями ошибок валидации.

        LOG.info("Received valid manual event DTO to send: transactionId={}", eventDto.getTransactionId());

        // --- Маппинг DTO в доменный объект InputEvent ---
        // Простой ручной маппинг (можно вынести в отдельный Mapper класс/компонент)
        InputEvent event = InputEventDto.mapDtoToEvent(eventDto);
        // ---------------------------------------------

        try {
            manualMessageService.sendManualEvent(event);
            return ResponseEntity.ok("Manual event with eventId '" + event.getEventId() + "' accepted for sending.");

        } catch (Exception e) { // Ловим ошибки от сервиса или Kafka
            LOG.error("An unexpected error occurred while accepting manual message for eventId: {}. Error: {}", event.getEventId(), e.getMessage(), e);
            // Отдаем общую ошибку сервера, т.к. запрос прошел валидацию DTO
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to accept event for sending: " + e.getMessage());
        }
    }
}