package com.example.kintai.domain.shift.controller;

import com.example.kintai.domain.shift.dto.CalendarEventDto;
import com.example.kintai.domain.shift.dto.ShiftConfirmDto;
import com.example.kintai.domain.shift.dto.ShiftRequestDto;
import com.example.kintai.domain.shift.service.ShiftService;
import com.example.kintai.global.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shifts")
public class ShiftApiController {

    private final ShiftService shiftService;

    public ShiftApiController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    /**
     * 월간 캘린더 이벤트 목록 조회
     */
    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventDto>> getEvents(
            @RequestParam("storeId") Long storeId,
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        List<CalendarEventDto> events = shiftService.getCalendarEvents(storeId, start, end);
        return ResponseEntity.ok(events);
    }

    /**
     * 알바생 희망 시프트 제출
     */
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<Long>> requestShift(@RequestBody ShiftRequestDto requestDto) {
        Long shiftId = shiftService.requestShift(requestDto);
        return ResponseEntity.ok(ApiResponse.ok("希望シフトが登録されました。", shiftId));
    }

    /**
     * 점주 시프트 확정
     */
    @PutMapping("/{shiftId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmShift(
            @PathVariable("shiftId") Long shiftId,
            @RequestBody ShiftConfirmDto confirmDto
    ) {
        shiftService.confirmShift(shiftId, confirmDto);
        return ResponseEntity.ok(ApiResponse.ok("シフトが確定されました。", null));
    }
}
