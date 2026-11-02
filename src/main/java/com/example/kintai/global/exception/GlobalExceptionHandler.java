package com.example.kintai.global.exception;

import com.example.kintai.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 入力値バリデーション (@Valid) 失敗時のハンドリング
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(errorMessage));
    }

    // ビジネスロジック例外 (IllegalArgumentException / IllegalStateException) のハンドリング
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage()));
    }

    // その他サーバー内部例外のハンドリング（SQLやスタックトレースの隠蔽およびロギング）
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex, HttpServletRequest request) throws Exception {
        // HTML画面リクエストの場合はデフォルトのエラーページへ委譲
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html") && !request.getRequestURI().startsWith("/api/")) {
            throw ex;
        }
        log.error("Unhandled exception at {}: ", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("サーバー内部エラーが発生しました。しばらくしてから再度お試しください。"));
    }
}
