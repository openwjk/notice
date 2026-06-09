package com.jkoi.notice.controller;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Collections;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Map<String, Object> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return Collections.emptyMap();
    }
}
