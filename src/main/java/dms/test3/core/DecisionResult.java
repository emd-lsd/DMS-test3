package dms.test3.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DecisionResult {
    public InputEvent originalEvent; // Включаем исходное событие
    public String riskScore;

    // Пустой конструктор
    public DecisionResult() {}

    public DecisionResult(InputEvent originalEvent, String riskScore) {
        this.originalEvent = originalEvent;
        this.riskScore = riskScore;
    }

    // Геттеры и сеттеры

    @Override
    public String toString() {
        return "DecisionResult{" +
                "originalEvent=" + originalEvent +
                ", riskScore='" + riskScore + '\'' +
                '}';
    }
}