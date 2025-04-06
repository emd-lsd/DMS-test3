package dms.test3.repository;

import dms.test3.dto.RiskDistributionDto;
import dms.test3.entity.DecisionResultEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository // Аннотация, указывающая, что это Spring Data репозиторий
public interface DecisionResultRepository extends JpaRepository<DecisionResultEntity, Long> { // <Тип Сущности, Тип ID>

    // Spring Data JPA автоматически сгенерирует реализацию базовых CRUD операций (save, findById, findAll, delete и т.д.)

    // Можно добавить кастомные методы поиска, если нужно, например:
    Optional<DecisionResultEntity> findByDecisionId(String decisionId);
    // List<DecisionResultEntity> findByRiskLevel(String riskLevel);

    // Метод для получения распределения по уровням риска
    // Используем конструктор DTO в JPQL запросе
    @Query("SELECT new dms.test3.dto.RiskDistributionDto(dr.riskLevel, count(dr)) " +
            "FROM DecisionResultEntity dr " +
            "GROUP BY dr.riskLevel " +
            "ORDER BY dr.riskLevel")
    List<RiskDistributionDto> getRiskLevelDistribution();

    // --- НОВЫЙ Метод для получения последних N записей ---
    // Spring Data JPA поймет, что нужно вернуть список сущностей,
    // а лимит и сортировка будут заданы через объект Pageable.
    // Явный @Query не обязателен, если нас устраивает стандартный поиск.
    // Но если хотим гарантировать конкретный запрос:
    @Query("SELECT dr FROM DecisionResultEntity dr") // Простой запрос
    List<DecisionResultEntity> findRecent(Pageable pageable); // Метод принимает Pageable

    // Можно также использовать стандартный findBy... метод, если сортировка по ID устраивает:
    // List<DecisionResultEntity> findByOrderByIdDesc(Pageable pageable);

}