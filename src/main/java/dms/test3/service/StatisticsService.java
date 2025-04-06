package dms.test3.service;

import dms.test3.dto.DecisionResultDto;
import dms.test3.dto.RiskDistributionDto;
import dms.test3.dto.StatisticsSummaryDto;
import dms.test3.entity.DecisionResultEntity;
import dms.test3.repository.DecisionResultRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Добавим транзакционность для чтения

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private static final Logger LOG = LoggerFactory.getLogger(StatisticsService.class);
    private final DecisionResultRepository decisionResultRepository;

    /**
     * Получает сводную статистику по принятым решениям.
     * @return DTO со статистикой.
     */
    @Transactional(readOnly = true) // Указываем, что транзакция только для чтения (оптимизация)
    public StatisticsSummaryDto getStatisticsSummary() {
        LOG.info("Calculating statistics summary...");

        long totalDecisions = decisionResultRepository.count(); // Используем встроенный метод
        List<RiskDistributionDto> riskDistribution = decisionResultRepository.getRiskLevelDistribution();

        StatisticsSummaryDto summary = new StatisticsSummaryDto(totalDecisions, riskDistribution);
        LOG.info("Statistics summary calculated: total={}, distribution size={}",
                summary.getTotalDecisions(), summary.getRiskDistribution().size());
        return summary;
    }


    // --- НОВЫЙ Метод для получения последних решений ---
    /**
     * Получает список последних N принятых решений.
     * @param limit Максимальное количество решений для возврата.
     * @return Список DTO последних решений.
     */
    @Transactional(readOnly = true)
    public List<DecisionResultDto> getRecentDecisions(int limit) {
        if (limit <= 0) {
            return Collections.emptyList(); // Возвращаем пустой список, если лимит некорректен
        }
        LOG.info("Fetching last {} decisions...", limit);

        // Создаем Pageable: страница 0, размер limit, сортировка по ID (или processingTimestamp) по убыванию
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "id"));
        // или Sort.by(Sort.Direction.DESC, "processingTimestamp")

        List<DecisionResultEntity> recentEntities = decisionResultRepository.findRecent(pageable);

        // Маппим Entity в DTO
        List<DecisionResultDto> recentDtos = recentEntities.stream()
                .map(this::mapEntityToDto) // Используем приватный метод маппинга
                .collect(Collectors.toList());

        LOG.info("Fetched {} recent decisions.", recentDtos.size());
        return recentDtos;
    }

    // Приватный метод для маппинга Entity -> DTO
    private DecisionResultDto mapEntityToDto(DecisionResultEntity entity) {
        if (entity == null) {
            return null;
        }
        // Убедитесь, что поля в DTO соответствуют полям в Entity
        return new DecisionResultDto(
                entity.getDecisionId(),
                entity.getRiskLevel(),
                entity.getEventId(), // Эти поля скопированы из Event в Entity
                entity.getAmount(),
                entity.getCurrency(),
                entity.getCountry(),
                entity.getProcessingTimestamp() // Это поле устанавливается при создании Entity
        );
    }
}
