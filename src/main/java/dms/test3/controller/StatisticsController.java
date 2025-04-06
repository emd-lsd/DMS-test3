package dms.test3.controller;

import dms.test3.dto.DecisionResultDto;
import dms.test3.dto.StatisticsSummaryDto;
import dms.test3.service.StatisticsService; // Импортируем сервис
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api") // Базовый путь для статистики
@RequiredArgsConstructor
public class StatisticsController {

    private static final Logger LOG = LoggerFactory.getLogger(StatisticsController.class);
    private final StatisticsService statisticsService; // Внедряем сервис

    @GetMapping("/statistics/summary")
    public ResponseEntity<StatisticsSummaryDto> getStatisticsSummary() {
        LOG.debug("Request received for GET /api/statistics/summary");
        try {
            StatisticsSummaryDto summary = statisticsService.getStatisticsSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            LOG.error("Error retrieving statistics summary", e);
            return ResponseEntity.internalServerError().body(null); // Отдаем 500 без тела или с DTO ошибки
        }
    }

    // --- НОВЫЙ Эндпоинт для последних решений ---
    @GetMapping("/decisions/recent") // Путь относительно /api
    public ResponseEntity<List<DecisionResultDto>> getRecentDecisions(
            // Принимаем параметр limit, по умолчанию 10
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        LOG.debug("Request received for GET /api/decisions/recent with limit: {}", limit);

        // Ограничиваем лимит для безопасности/производительности
        if (limit <= 0) {
            limit = 10;
        } else if (limit > 50) { // Установим разумный верхний предел
            LOG.warn("Requested limit {} exceeds maximum allowed (50), setting to 50.", limit);
            limit = 50;
        }

        try {
            List<DecisionResultDto> recentDecisions = statisticsService.getRecentDecisions(limit);
            return ResponseEntity.ok(recentDecisions);
        } catch (Exception e) {
            LOG.error("Error retrieving recent decisions", e);
            // Возвращаем пустой список или 500 ошибку
            return ResponseEntity.internalServerError().body(Collections.emptyList());
        }
    }
}
