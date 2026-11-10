package com.example.demo.vo;

import java.math.BigDecimal;

/**
 * @author ytl
 * @date 2026/7/2 21:57
 * @description TODO
 */
public record MovieInfoVO(String title, String type, int year, BigDecimal rating, String reason) {
}
