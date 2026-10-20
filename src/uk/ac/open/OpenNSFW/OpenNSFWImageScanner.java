/*
 *  OpenNSFW
 *  Copyright (C) 2015 Blaize Strothers
 *  
 *  Derived from SmutDetectImageScanner by Rajmund Witt
 */
package uk.ac.open.OpenNSFW;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

// Checks an image file from Autopsy for NSFW content
public abstract class OpenNSFWImageScanner {

    private static final Logger logger_ = Logger.getLogger(OpenNSFWImageScanner.class.getName());

    private static final OpenNSFWNsfwClassifier classifier_ = new OpenNSFWNsfwClassifier();

    /** Scans with the classifier's built-in default threshold. */
    public static OpenNSFWDetectedImage scanImage(AbstractFile file) {
        return scanImage(file, OpenNSFWNsfwClassifier.NSFW_THRESHOLD);
    }

    /**
     * @param threshold NSFW probability (0.0 - 1.0) at or above which the
     *                  image is flagged
     * @return a OpenNSFWDetectedImage carrying the score for EVERY image that
     *         was classified (isNsfw() says whether it reached the threshold),
     *         or null if the image was unreadable or classification failed
     */
    public static OpenNSFWDetectedImage scanImage(AbstractFile file, double threshold) {
        try (InputStream inputStream = new ReadContentInputStream(file)) {
            BufferedImage bufferedImage = ImageIO.read(inputStream);
            if (bufferedImage == null) {
                return null;
            }

            double score = classifier_.classify(bufferedImage);

            OpenNSFWDetectedImage detectedImage = new OpenNSFWDetectedImage();
            detectedImage.setNsfw(score >= threshold);
            detectedImage.setNsfwScore(score);
            return detectedImage;

        } catch (LinkageError e) {
            // A broken ImageIO plugin (e.g. the WebP reader whose native DLL
            // won't load) throws NoClassDefFoundError / UnsatisfiedLinkError /
            // ExceptionInInitializerError. Skip the file instead of failing it.
            logger_.log(Level.WARNING,
                    "Image decoder unavailable for " + file.getName() + ": " + e.toString());
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            logger_.log(Level.WARNING,
                    "Error classifying image " + file.getName() + ": " + e.toString());
        }
        return null;
    }
}
