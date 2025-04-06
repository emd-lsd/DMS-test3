package dms.test3.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecisionResultDto {
    // Поля, которые хотим показывать в "Live Log"
    private String decisionId;
    private String riskLevel;
    private String eventId; // Из вложенного originalEvent
    private BigDecimal amount; // Из вложенного originalEvent
    private String currency; // Из вложенного originalEvent
    private String country; // Из вложенного originalEvent
    private Instant processingTimestamp; // Время записи в БД
}
