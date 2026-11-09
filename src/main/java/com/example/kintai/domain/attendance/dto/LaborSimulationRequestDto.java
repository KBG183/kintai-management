package com.example.kintai.domain.attendance.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日本労働基準法第37条に基づく時間外・深夜割増シミュレーションリクエストDTO。
 */
public class LaborSimulationRequestDto {

    private LocalDateTime clockInAt;
    private LocalDateTime clockOutAt;
    private Integer hourlyWage;
    private List<SimulationBreakDto> breaks;

    public static class SimulationBreakDto {
        private LocalDateTime startAt;
        private LocalDateTime endAt;

        public SimulationBreakDto() {}

        public SimulationBreakDto(LocalDateTime startAt, LocalDateTime endAt) {
            this.startAt = startAt;
            this.endAt = endAt;
        }

        public LocalDateTime getStartAt() { return startAt; }
        public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }

        public LocalDateTime getEndAt() { return endAt; }
        public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    }

    public LaborSimulationRequestDto() {}

    public LocalDateTime getClockInAt() { return clockInAt; }
    public void setClockInAt(LocalDateTime clockInAt) { this.clockInAt = clockInAt; }

    public LocalDateTime getClockOutAt() { return clockOutAt; }
    public void setClockOutAt(LocalDateTime clockOutAt) { this.clockOutAt = clockOutAt; }

    public Integer getHourlyWage() { return hourlyWage; }
    public void setHourlyWage(Integer hourlyWage) { this.hourlyWage = hourlyWage; }

    public List<SimulationBreakDto> getBreaks() { return breaks; }
    public void setBreaks(List<SimulationBreakDto> breaks) { this.breaks = breaks; }
}
