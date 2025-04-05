package dms.test3.repository;

import dms.test3.entity.DecisionResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository // Аннотация, указывающая, что это Spring Data репозиторий
public interface DecisionResultRepository extends JpaRepository<DecisionResultEntity, Long> { // <Тип Сущности, Тип ID>

    // Spring Data JPA автоматически сгенерирует реализацию базовых CRUD операций (save, findById, findAll, delete и т.д.)

    // Можно добавить кастомные методы поиска, если нужно, например:
    Optional<DecisionResultEntity> findByDecisionId(String decisionId);
    // List<DecisionResultEntity> findByRiskLevel(String riskLevel);

}