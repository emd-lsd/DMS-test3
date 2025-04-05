package dms.test3.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InputEvent {
    public String eventId;         // Уникальный ID события/транзакции
    public String transactionId;   // ID самой транзакции (может совпадать с eventId или быть отдельным)
    public String customerId;      // ID клиента, совершающего транзакцию
    public BigDecimal amount;          // Сумма транзакции (уже есть)
    public String currency;        // Валюта транзакции (например, "USD", "EUR", "RUB")
    public String country;         // Страна, из которой инициирована транзакция (ISO код, уже есть)
    public String paymentMethod;   // Метод платежа ("CREDIT_CARD", "BANK_TRANSFER", "E_WALLET", "CRYPTO")
    public Integer customerAge;     // Возраст клиента
    public Integer customerHistoryScore; // Некий скоринг клиента (0-100, чем выше, тем надежнее)
    public Boolean isNewDevice;     // Флаг, используется ли новое/неизвестное устройство для этой транзакции
}