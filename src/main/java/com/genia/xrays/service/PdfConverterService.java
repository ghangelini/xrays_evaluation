package com.genia.xrays.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Service
public class PdfConverterService {

    private static final float PDF_RENDER_DPI = 200.0f;

    /**
     * Converts the first page of a PDF (as raw bytes) to a BufferedImage.
     * @param pdfBytes the raw bytes of the PDF file
     * @return BufferedImage of the first page
     */
    public BufferedImage convertFirstPageToImage(byte[] pdfBytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() == 0) {
                throw new IllegalArgumentException("El PDF no contiene páginas.");
            }
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            BufferedImage image = pdfRenderer.renderImageWithDPI(0, PDF_RENDER_DPI);
            return image;
        }
    }
}
