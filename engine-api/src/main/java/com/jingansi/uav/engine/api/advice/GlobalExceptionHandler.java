package com.jingansi.uav.engine.api.advice;

import com.jingansi.uav.engine.common.exception.BizException;
import com.jingansi.uav.engine.common.vo.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;

/**
 * 全局异常处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Response<Void>> handleBizException(BizException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Response.error(ex.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Response<Void>> handleBadRequestException(Exception ex) {
        log.warn("请求参数异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Response.error(resolveBadRequestMessage(ex)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleException(Exception ex, HttpServletRequest request) {
        log.error("接口处理异常, uri={}", request == null ? null : request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Response.error("系统异常，请联系管理员"));
    }

    private String resolveBadRequestMessage(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException exception = (MethodArgumentNotValidException) ex;
            if (exception.getBindingResult().getFieldError() != null) {
                return exception.getBindingResult().getFieldError().getDefaultMessage();
            }
        }
        if (ex instanceof BindException) {
            BindException exception = (BindException) ex;
            if (exception.getBindingResult().getFieldError() != null) {
                return exception.getBindingResult().getFieldError().getDefaultMessage();
            }
        }
        if (ex instanceof ConstraintViolationException) {
            ConstraintViolationException exception = (ConstraintViolationException) ex;
            if (!exception.getConstraintViolations().isEmpty()) {
                return exception.getConstraintViolations().iterator().next().getMessage();
            }
        }
        if (ex instanceof MissingServletRequestParameterException) {
            return ex.getMessage();
        }
        if (ex instanceof MethodArgumentTypeMismatchException) {
            return "请求参数类型错误";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "请求体格式错误";
        }
        return "请求参数错误";
    }
}
