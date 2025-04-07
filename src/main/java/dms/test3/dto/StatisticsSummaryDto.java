package dms.test3.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsSummaryDto {
    private Long totalDecisions;
    private List<RiskDistributionDto> riskDistribution;
}
