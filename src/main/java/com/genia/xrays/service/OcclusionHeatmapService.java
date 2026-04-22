package com.genia.xrays.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Collections;

/**
 * Generates an Occlusion Sensitivity Map as a heatmap.
 *
 * Algorithm:
 *  1. Divide the image into a GRID_SIZE × GRID_SIZE grid.
 *  2. For each cell, replace it with a neutral gray patch.
 *  3. Run inference → record the drop in confidence for the predicted class.
 *  4. A larger drop means that region is more important to the model.
 *  5. Colour the importance map using a Jet colormap and blend onto the original.
 */
@Service
public class OcclusionHeatmapService {

    private static final int GRID_SIZE = 7;       // 7×7 occlusion grid
    private static final int MODEL_SIZE = 224;
    private static final int FILL_COLOR = 127;    // neutral gray occlusion patch

    /**
     * Generates a heatmap overlay and returns it as a Base64-encoded JPEG.
     *
     * @param originalImg   the already-decoded input image
     * @param targetClassId the class ID that was predicted (we measure drop for this class)
     * @param env           ONNX Runtime environment
     * @param session       ONNX Runtime session
     * @return Base64 string of the overlay JPEG
     */
    public String generateHeatmap(BufferedImage originalImg, int targetClassId,
                                  OrtEnvironment env, OrtSession session) throws Exception {

        // --- 1. Resize original to model input size ---
        BufferedImage resized = resize(originalImg, MODEL_SIZE, MODEL_SIZE);

        // --- 2. Baseline confidence ---
        float baselineConf = runInference(resized, targetClassId, env, session);

        // --- 3. Build importance map ---
        int cellW = MODEL_SIZE / GRID_SIZE;
        int cellH = MODEL_SIZE / GRID_SIZE;
        float[][] importance = new float[GRID_SIZE][GRID_SIZE];
        float maxDrop = 1e-6f;

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                BufferedImage occluded = deepCopy(resized);

                // Paint the cell gray
                Graphics2D g = occluded.createGraphics();
                g.setColor(new Color(FILL_COLOR, FILL_COLOR, FILL_COLOR));
                g.fillRect(col * cellW, row * cellH, cellW, cellH);
                g.dispose();

                float conf = runInference(occluded, targetClassId, env, session);
                float drop = Math.max(0f, baselineConf - conf);
                importance[row][col] = drop;
                if (drop > maxDrop) maxDrop = drop;
            }
        }

        // --- 4. Normalize importance to [0,1] ---
        for (int r = 0; r < GRID_SIZE; r++)
            for (int c = 0; c < GRID_SIZE; c++)
                importance[r][c] /= maxDrop;

        // --- 5. Build a smooth heatmap at MODEL_SIZE × MODEL_SIZE ---
        BufferedImage heatmap = new BufferedImage(MODEL_SIZE, MODEL_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < MODEL_SIZE; y++) {
            for (int x = 0; x < MODEL_SIZE; x++) {
                int row = Math.min((int)(y / (float) MODEL_SIZE * GRID_SIZE), GRID_SIZE - 1);
                int col = Math.min((int)(x / (float) MODEL_SIZE * GRID_SIZE), GRID_SIZE - 1);
                float val = importance[row][col];
                Color jet = jetColor(val, 0.55f); // alpha 55%
                heatmap.setRGB(x, y, jet.getRGB());
            }
        }

        // --- 6. Overlay heatmap onto the original resized image ---
        BufferedImage overlay = deepCopy(resized);
        Graphics2D g2 = overlay.createGraphics();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g2.drawImage(heatmap, 0, 0, null);
        g2.dispose();

        // --- 7. Encode to Base64 JPEG ---
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(overlay, "jpg", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private float runInference(BufferedImage img, int targetClassId,
                                OrtEnvironment env, OrtSession session) throws Exception {
        float[] data = toFloatArray(img);
        long[] shape = {1, 3, MODEL_SIZE, MODEL_SIZE};
        try (OnnxTensor t = OnnxTensor.createTensor(env, java.nio.FloatBuffer.wrap(data), shape);
             OrtSession.Result res = session.run(Collections.singletonMap("images", t))) {
            float[][] out = (float[][]) res.get(0).getValue();
            return out[0][targetClassId];
        }
    }

    private float[] toFloatArray(BufferedImage img) {
        float[] f = new float[3 * MODEL_SIZE * MODEL_SIZE];
        for (int y = 0; y < MODEL_SIZE; y++) {
            for (int x = 0; x < MODEL_SIZE; x++) {
                int rgb = img.getRGB(x, y);
                f[0 * MODEL_SIZE * MODEL_SIZE + y * MODEL_SIZE + x] = ((rgb >> 16) & 0xFF) / 255f;
                f[1 * MODEL_SIZE * MODEL_SIZE + y * MODEL_SIZE + x] = ((rgb >> 8)  & 0xFF) / 255f;
                f[2 * MODEL_SIZE * MODEL_SIZE + y * MODEL_SIZE + x] = ( rgb        & 0xFF) / 255f;
            }
        }
        return f;
    }

    private BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return out;
    }

    private BufferedImage deepCopy(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    /** Maps value in [0,1] to a Jet colormap RGBA color. */
    private Color jetColor(float t, float alpha) {
        float r = clamp(1.5f - Math.abs(4f * t - 3f), 0, 1);
        float g = clamp(1.5f - Math.abs(4f * t - 2f), 0, 1);
        float b = clamp(1.5f - Math.abs(4f * t - 1f), 0, 1);
        return new Color(r, g, b, alpha);
    }

    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
