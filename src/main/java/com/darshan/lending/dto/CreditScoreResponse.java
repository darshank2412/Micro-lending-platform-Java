package com.darshan.lending.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreditScoreResponse {
    private Long   borrowerId;
    private String borrowerName;
    private int    score;
    private int    maxScore;
    private String grade;          // EXCELLENT / GOOD / FAIR / POOR
    private String breakdown;      // per-component breakdown
    private String recommendation;
}