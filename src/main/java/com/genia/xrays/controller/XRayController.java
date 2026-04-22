package com.genia.xrays.controller;

import com.genia.xrays.model.XRayResult;
import com.genia.xrays.service.InferenceService;
import com.genia.xrays.service.PdfConverterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/xrays")
@RequiredArgsConstructor
public class XRayController {

    private final InferenceService inferenceService;
    private final PdfConverterService pdfConverterService;

    /** Imagen en Base64 (JPEG/PNG). Param ?heatmap=true para recibir el mapa de calor. */
    @PostMapping("/evaluate")
    public ResponseEntity<XRayResult> evaluate(
            @RequestBody Map<String, String> payload,
            @RequestParam(value = "heatmap", defaultValue = "false") boolean heatmap) {

        String base64Image = payload.get("image_base64");
        if (base64Image == null || base64Image.isEmpty()) return ResponseEntity.badRequest().build();

        try {
            return ResponseEntity.ok(inferenceService.predict(base64Image, heatmap));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** PDF como archivo multipart. Param ?heatmap=true para recibir el mapa de calor. */
    @PostMapping("/evaluate/pdf")
    public ResponseEntity<XRayResult> evaluatePdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "heatmap", defaultValue = "false") boolean heatmap) {

        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        try {
            byte[] pdfBytes = file.getBytes();
            BufferedImage img = pdfConverterService.convertFirstPageToImage(pdfBytes);
            return ResponseEntity.ok(inferenceService.predictFromImage(img, heatmap));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** PDF en Base64. Param ?heatmap=true para recibir el mapa de calor. */
    @PostMapping("/evaluate/pdf/base64")
    public ResponseEntity<XRayResult> evaluatePdfBase64(
            @RequestBody Map<String, String> payload,
            @RequestParam(value = "heatmap", defaultValue = "false") boolean heatmap) {

        String base64Pdf = payload.get("pdf_base64");
        if (base64Pdf == null || base64Pdf.isEmpty()) return ResponseEntity.badRequest().build();
        try {
            String raw = base64Pdf.contains(",") ? base64Pdf.split(",")[1] : base64Pdf;
            byte[] pdfBytes = Base64.getDecoder().decode(raw);
            BufferedImage img = pdfConverterService.convertFirstPageToImage(pdfBytes);
            return ResponseEntity.ok(inferenceService.predictFromImage(img, heatmap));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
