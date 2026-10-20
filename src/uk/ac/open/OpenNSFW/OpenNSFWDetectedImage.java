/*
 *  OpenNSFW
 *  Copyright (C) 2015 Blaize Strothers
 *  
 *  Derived from SmutDetectCategorisedImage by Rajmund Witt
 */
package uk.ac.open.OpenNSFW;

import java.util.Locale;

public class OpenNSFWDetectedImage {

    private boolean nsfw_ = false;
    private double nsfwScore_ = 0.0;

    public OpenNSFWDetectedImage() {}

    public boolean isNsfw() {
        return nsfw_;
    }

    public void setNsfw(boolean nsfw) {
        nsfw_ = nsfw;
    }

    public double getNsfwScore() {
        return nsfwScore_;
    }

    public void setNsfwScore(double nsfwScore) {
        nsfwScore_ = nsfwScore;
    }

    /**
     * @return the DetectedImage as a string for report creation
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "NSFW: %b (score %.3f)", isNsfw(), getNsfwScore());
    }
}
