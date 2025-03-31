package dms.test3.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true) // Игнорировать лишние поля в JSON
public class InputEvent {
    public String eventId;
    public Double amount; // Используем Double для совместимости с DMN
    public String country;

    // Пустой конструктор для Jackson/Flink
    public InputEvent() {}

    public InputEvent(String eventId, Double amount, String country) {
        this.eventId = eventId;
        this.amount = amount;
        this.country = country;
    }

    // Геттеры и сеттеры (можно сгенерировать в IDE)
    // или сделать поля public, как здесь для простоты

    @Override
    public String toString() {
        return "InputEvent{" +
                "eventId='" + eventId + '\'' +
                ", amount=" + amount +
                ", country='" + country + '\'' +
                '}';
    }
}