package com.darshan.lending.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchRequest {
    @Builder.Default
    private Integer maxOffers = 5;
}