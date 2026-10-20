/*
 *  OpenNSFW
 *  Copyright (C) 2015 Blaize Strothers
 *
 *  NSFW image classification using the OpenNSFW open_nsfw model exported to ONNX,
 *  run through OpenCV's DNN module (Dnn.readNetFromONNX).
 */
package uk.ac.open.OpenNSFW;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

public class OpenNSFWNsfwClassifier {

    // Model file, placed in uk/ac/open/OpenNSFW/ next to this class
    private static final String MODEL_RESOURCE = "open_nsfw.onnx";

    // Images with an NSFW probability at or above this are flagged
    public static final double NSFW_THRESHOLD = 0.40;

    // open_nsfw preprocessing: resize to 256x256, centre-crop 224x224,
    // BGR channel order, raw 0-255 values minus the per-channel mean
    private static final int RESIZE_SIZE = 256;
    private static final int INPUT_SIZE = 224;
    private static final Scalar MEAN_BGR = new Scalar(104.0, 117.0, 123.0);

    // Index of the "NSFW" class in the 2-element softmax output ([SFW, NSFW])
    private static final int NSFW_INDEX = 1;

    private static File modelFile_ = null;

    // dnn.Net is not thread-safe and Autopsy runs file ingest modules on several threads
    private static final ThreadLocal<Net> nets_ = new ThreadLocal<>();

    /** Copies the ONNX model out of the JAR to a temp file (once) because OpenCV needs a real path. */
    private static synchronized File getModelFile() throws IOException {
        if (modelFile_ == null || !modelFile_.exists()) {
            try (InputStream in = OpenNSFWNsfwClassifier.class.getResourceAsStream(MODEL_RESOURCE)) {
                if (in == null) {
                    throw new IOException("Model resource not found: " + MODEL_RESOURCE);
                }
                File tmp = File.createTempFile("open_nsfw", ".onnx");
                tmp.deleteOnExit();
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                modelFile_ = tmp;
            }
        }
        return modelFile_;
    }

    private static Net getNet() throws IOException {
        Net net = nets_.get();
        if (net == null) {
            net = Dnn.readNetFromONNX(getModelFile().getPath());
            nets_.set(net);
        }
        return net;
    }

    /**
     * Start-up self-test: confirms the model resource is in the JAR and that
     * OpenCV can parse it. Call after the native library is loaded so a broken
     * install fails loudly instead of every image silently scoring "not NSFW".
     */
    public static void verifyModel() throws IOException {
        // Run a real (blank) image through the whole pipeline, not just the
        // model parser: shape/layout mismatches only show up at forward().
        new OpenNSFWNsfwClassifier().classify(
                new BufferedImage(INPUT_SIZE, INPUT_SIZE, BufferedImage.TYPE_3BYTE_BGR));
    }

    /** Returns the image as packed 3-byte BGR (also flattens alpha/palette/gray images). */
    private static BufferedImage toBgr(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return src;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * Classifies an image.
     *
     * @return probability (0.0 - 1.0) that the image is NSFW
     */
    public double classify(BufferedImage bufferedImage) {
        Mat image = null;
        Mat resized = null;
        Mat floatImg = null;
        Mat blob = null;
        Mat output = null;
        try {
            BufferedImage bgr = toBgr(bufferedImage);
            byte[] pixels = ((DataBufferByte) bgr.getRaster().getDataBuffer()).getData();

            image = new Mat(bgr.getHeight(), bgr.getWidth(), CvType.CV_8UC3);
            image.put(0, 0, pixels);

            // Squash to 256x256 then centre-crop 224x224 (matches the original Caffe pipeline)
            resized = new Mat();
            Imgproc.resize(image, resized, new Size(RESIZE_SIZE, RESIZE_SIZE), 0, 0, Imgproc.INTER_LINEAR);
            int off = (RESIZE_SIZE - INPUT_SIZE) / 2;
            Mat roi = new Mat(resized, new Rect(off, off, INPUT_SIZE, INPUT_SIZE));
            Mat cont = roi.clone(); // an ROI is not continuous; reshape() needs a continuous Mat
            roi.release();

            // This ONNX model takes NHWC input [1, 224, 224, 3] (it starts with its
            // own Transpose to NCHW). Dnn.blobFromImage would give NCHW
            // [1, 3, 224, 224], which makes the first convolution see 224 channels
            // ("Number of input channels should be multiple of 3 but got 224").
            // So build the blob by hand: BGR, float, mean-subtracted, NHWC.
            floatImg = new Mat();
            cont.convertTo(floatImg, CvType.CV_32FC3);
            cont.release();
            Core.subtract(floatImg, MEAN_BGR, floatImg);
            blob = floatImg.reshape(1, new int[]{1, INPUT_SIZE, INPUT_SIZE, 3});

            Net net = getNet();
            net.setInput(blob);
            output = net.forward();

            // Expect [1 x 2] = {sfw, nsfw}; tolerate models that output a single NSFW score
            long count = output.total();
            double[] v = new double[(int) count];
            Mat flat = output.reshape(1, 1);
            for (int i = 0; i < count; i++) {
                v[i] = flat.get(0, i)[0];
            }
            double score = (count >= 2) ? v[NSFW_INDEX] : v[0];
            return Math.max(0.0, Math.min(1.0, score));
        } catch (IOException ex) {
            throw new RuntimeException("Unable to load NSFW ONNX model", ex);
        } finally {
            if (image != null) image.release();
            if (resized != null) resized.release();
            if (floatImg != null) floatImg.release();
            if (blob != null) blob.release();
            if (output != null) output.release();
        }
    }

    public boolean isNsfw(double score) {
        return score >= NSFW_THRESHOLD;
    }
}
