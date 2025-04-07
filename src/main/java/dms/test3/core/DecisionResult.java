package dms.test3.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DecisionResult {
    public InputEvent originalEvent; // Исходное событие
    public String decisionId;       // Уникальный ID принятого решения
    public String riskLevel;        // Уровень риска ("LOW", "MEDIUM", "HIGH", "REVIEW")
    // Позже можно добавить:
    // public List<String> reasonCodes; // Коды причин, почему присвоен такой уровень риска
}