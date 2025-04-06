package dms.test3.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskDistributionDto {
    private String riskLevel;
    private Long count; // Используем Long, т.к. count(*) возвращает Long
}
