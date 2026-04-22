package com.genia.xrays.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XRayResult {
    private String label;
    private double confidence;
    private int classId;
    private String modelUsed;
    private String heatmapBase64;   // Base64 overlay image (null = not requested)
}
