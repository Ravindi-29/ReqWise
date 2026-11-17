package com.reqwise.dto;

public record BoundingBox(
    float x0,
    float y0,
    float x1,
    float y1
) {
    public static BoundingBox empty() {
        return new BoundingBox(0f, 0f, 0f, 0f);
    }
}
