/*
 *  OpenNSFW
 *  Copyright (C) 2015 Blaize Strothers
 * 
 *  Derived from Sample Module provided with Autopsy 3.1.
 * 
 *  This is free and unencumbered software released into the public domain.
 *  
 *  Anyone is free to copy, modify, publish, use, compile, sell, or
 *  distribute this software, either in source code form or as a compiled
 *  binary, for any purpose, commercial or non-commercial, and by any
 *  means.
 *  
 *  In jurisdictions that recognize copyright laws, the author or authors
 *  of this software dedicate any and all copyright interest in the
 *  software to the public domain. We make this dedication for the benefit
 *  of the public at large and to the detriment of our heirs and
 *  successors. We intend this dedication to be an overt act of
 *  relinquishment in perpetuity of all present and future rights to this
 *  software under copyright law.
 *  
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 *  IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 *  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 *  OTHER DEALINGS IN THE SOFTWARE. 
 */
package uk.ac.open.OpenNSFW.autopsy;

import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

public class OpenNSFWIngestJobSettings implements IngestModuleIngestJobSettings {

    // Bumped from 1 because fields were added: settings saved by the old class
    // would otherwise deserialise with useThumbnail == false and minSize == 0
    // (field initialisers do not run on deserialisation). Autopsy discards
    // settings whose version number differs and uses the defaults instead.
    private static final long serialVersionUID = 3L;

    private boolean skipKnownFiles = true;
    private boolean useThumbnail = true;
    private long minSize = 100;
    /** Detection threshold: NSFW score as a percentage, 0 - 100. */
    private int threshold = 40;

    OpenNSFWIngestJobSettings() {
    }

    OpenNSFWIngestJobSettings(boolean skipKnownFiles, boolean useThumbnail, long minSize, int threshold) {
        this.skipKnownFiles = skipKnownFiles;
        this.useThumbnail = useThumbnail;
        this.minSize = minSize;
        this.threshold = clampThreshold(threshold);
    }

    private static int clampThreshold(int t) {
        return Math.max(0, Math.min(100, t));
    }

    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    void setSkipKnownFiles(boolean enabled) {
        skipKnownFiles = enabled;
    }

    void setUseThumbnail(boolean enabled) {
        useThumbnail = enabled;
    }

    void setMinSize(long minSize) {
        this.minSize = minSize;
    }

    void setThreshold(int threshold) {
        this.threshold = clampThreshold(threshold);
    }

    boolean skipKnownFiles() {
        return skipKnownFiles;
    }

    boolean useThumbnail() {
        return useThumbnail;
    }

    long minSizeFiles() {
        return minSize;
    }

    /** @return the detection threshold as a percentage, 0 - 100 */
    int threshold() {
        return threshold;
    }
}
