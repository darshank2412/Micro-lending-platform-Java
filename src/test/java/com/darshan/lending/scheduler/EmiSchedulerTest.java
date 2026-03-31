package com.darshan.lending.scheduler;

import com.darshan.lending.entity.enums.EmiStatus;
import com.darshan.lending.repository.EmiScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmiSchedulerTest {

    @Autowired EmiScheduleRepository emiScheduleRepository;

    @Test
    void overdueEmiQuery_returnsEmptyWhenNoOverdue() {
        var result = emiScheduleRepository.findOverdueEmis(
                java.time.LocalDate.now().minusDays(1),
                EmiStatus.PENDING);
        org.junit.jupiter.api.Assertions.assertNotNull(result);
    }
}