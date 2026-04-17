package com.genia.xrays.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.genia.xrays.model.XRayResult;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Collections;

@Service
public class InferenceService {

    @Getter
    private OrtEnvironment env;
    @Getter
    private OrtSession session;

    private final String modelPath = "models/xrays_evaluation_model_medium_v1.onnx";

    @Autowired
    private OcclusionHeatmapService heatmapService;

    @PostConstruct
    public void init() throws Exception {
        env = OrtEnvironment.getEnvironment();
        session = env.createSession(modelPath, new OrtSession.SessionOptions());
    }

    public XRayResult predict(String base64Image) throws Exception {
        return predict(base64Image, false);
    }

    public XRayResult predict(String base64Image, boolean withHeatmap) throws Exception {
        String raw = base64Image.contains(",") ? base64Image.split(",")[1] : base64Image;
        byte[] imageBytes = Base64.getDecoder().decode(raw);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (img == null) {
            throw new IllegalArgumentException("No se pudo decodificar la imagen.");
        }
        return predictFromImage(img, withHeatmap);
    }

    public XRayResult predictFromImage(BufferedImage img) throws Exception {
        return predictFromImage(img, false);
    }

    public XRayResult predictFromImage(BufferedImage img, boolean withHeatmap) throws Exception {
        float[] inputData = preprocess(img);
        long[] shape = new long[]{1, 3, 224, 224};
        OnnxTensor tensor = OnnxTensor.createTensor(env, java.nio.FloatBuffer.wrap(inputData), shape);

        try (OrtSession.Result results = session.run(Collections.singletonMap("images", tensor))) {
            float[][] output = (float[][]) results.get(0).getValue();

            int classId = argmax(output[0]);
            double confidence = output[0][classId];
            String label = (classId == 1) ? "Normal" : "Anomaly";

            String heatmap = null;
            if (withHeatmap) {
                heatmap = heatmapService.generateHeatmap(img, classId, env, session);
            }

            return XRayResult.builder()
                    .label(label)
                    .confidence(confidence)
                    .classId(classId)
                    .modelUsed("YOLO11m-cls (ONNX)")
                    .heatmapBase64(heatmap)
                    .build();
        }
    }

    private float[] preprocess(BufferedImage img) {
        Image scaledImage = img.getScaledInstance(224, 224, Image.SCALE_SMOOTH);
        BufferedImage resizedImg = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImg.createGraphics();
        g2d.drawImage(scaledImage, 0, 0, null);
        g2d.dispose();

        float[] floatValues = new float[3 * 224 * 224];
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 224; x++) {
                int rgb = resizedImg.getRGB(x, y);
                floatValues[0 * 224 * 224 + y * 224 + x] = ((rgb >> 16) & 0xFF) / 255.0f;
                floatValues[1 * 224 * 224 + y * 224 + x] = ((rgb >> 8)  & 0xFF) / 255.0f;
                floatValues[2 * 224 * 224 + y * 224 + x] = ( rgb        & 0xFF) / 255.0f;
            }
        }
        return floatValues;
    }

    private int argmax(float[] probabilities) {
        int maxIdx = -1;
        float maxVal = -Float.MAX_VALUE;
        for (int i = 0; i < probabilities.length; i++) {
            if (probabilities[i] > maxVal) {
                maxVal = probabilities[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }
}
