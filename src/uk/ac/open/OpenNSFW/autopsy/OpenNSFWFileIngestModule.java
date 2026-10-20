/*
 *  OpenNSFW
 *  Copyright (C) 2015 Blaize Strothers
 * 
 *  Derived from Sample Module provided with Autopsy 3.1 and SmutDetect4Autopsy by Rajmund Witt
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

import uk.ac.open.OpenNSFW.OpenNSFWDetectedImage;
import uk.ac.open.OpenNSFW.OpenNSFWImageScanner;
import uk.ac.open.OpenNSFW.OpenNSFWNativeLoader;
import uk.ac.open.OpenNSFW.OpenNSFWNsfwClassifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.AnalysisResultAdded;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

public class OpenNSFWFileIngestModule implements FileIngestModule {

    // Shared between module instances; access only via the synchronized
    // static methods below.
    private static final HashMap<Long, Long> artifactCountsForIngestJobs = new HashMap<>();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private final static String MODULE_NAME = OpenNSFWIngestModuleFactory.getModuleName();

    /** Enough bytes for the longest signature checked (JPEG 2000: 8). */
    private static final int HEADER_BYTES = 12;

    // Per-instance, per-job settings (must NOT be static: several ingest jobs
    // with different settings can run at the same time).
    private final boolean skipKnownFiles;
    private final boolean useThumbnail;
    private final long minSize;
    private final double threshold; // 0.0 - 1.0

    private IngestJobContext context = null;
    private Blackboard blackboard = null;

    OpenNSFWFileIngestModule(OpenNSFWIngestJobSettings settings) {
        this.skipKnownFiles = settings.skipKnownFiles();
        this.useThumbnail = settings.useThumbnail();
        this.minSize = settings.minSizeFiles();
        this.threshold = settings.threshold() / 100.0;
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        try {
            blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModuleException("No case is open", ex);
        }
        // Loaded here rather than in the constructor so a failure shows up as
        // a module start-up error the user can see, not a silent factory crash.
        try {
            OpenNSFWNativeLoader.load();
            OpenNSFWNsfwClassifier.verifyModel();
        } catch (Throwable t) {
            throw new IngestModuleException("Could not load OpenNSFW native libraries/model: " + t, t);
        }
        refCounter.incrementAndGet(context.getJobId());
    }

    @Override
    public ProcessResult process(AbstractFile file) {
        // Skip anything other than actual file system files.
        if ((file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)
                || (file.isFile() == false)) {
            return ProcessResult.OK;
        }

        // Skip NSRL / known files.
        if (skipKnownFiles && file.getKnown() == TskData.FileKnown.KNOWN) {
            return ProcessResult.OK;
        }

        // Skip unsupported file types
        if (!isImageFile(file)) {
            return ProcessResult.OK;
        }

        try {
            OpenNSFWDetectedImage image = OpenNSFWImageScanner.scanImage(file, threshold);
            if (image == null || !image.isNsfw()) {
                return ProcessResult.OK; // unreadable, failed (logged), or below threshold
            }

            Collection<BlackboardAttribute> attributes = new ArrayList<>();
            attributes.add(new BlackboardAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT), MODULE_NAME, image.toString()));
            attributes.add(new BlackboardAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME), MODULE_NAME, "OpenNSFW NSFW Images"));

            AnalysisResultAdded resultAdded = file.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_INTERESTING_ITEM,
                    Score.SCORE_LIKELY_NOTABLE,
                    "Possible NSFW image",
                    "OpenNSFW",
                    "Classifier score at or above threshold",
                    attributes,
                    file.getId());
            AnalysisResult result = resultAdded.getAnalysisResult();

            // Creating the result only writes it to the case database; posting
            // it is what makes it appear in the UI and notifies other modules.
            blackboard.postArtifact(result, MODULE_NAME, context.getJobId());

            addToBlackboardPostCount(context.getJobId(), 1L);

            IngestServices.getInstance().postMessage(
                    IngestMessage.createDataMessage(
                            MODULE_NAME,
                            "NSFW Image Detected",
                            "Image classified as NSFW (" + image + "): " + file.getName(),
                            String.valueOf(file.getId()),
                            result));
            return IngestModule.ProcessResult.OK;

        } catch (TskCoreException | Blackboard.BlackboardException ex) {
            Logger logger = IngestServices.getInstance().getLogger(MODULE_NAME);
            logger.log(Level.SEVERE, "Error processing file (id = " + file.getId() + ")", ex);
            return IngestModule.ProcessResult.ERROR;
        } catch (Throwable t) {
            Logger logger = IngestServices.getInstance().getLogger(MODULE_NAME);
            logger.log(Level.SEVERE, "Unexpected error processing file (id = " + file.getId() + ")", t);
            return IngestModule.ProcessResult.ERROR;
        }
    }

    @Override
    public void shutDown() {
        // startUp may have failed before the context was set.
        if (context != null) {
            // Always decrement the reference count (even if cancelled) so the
            // per-job entry is released; only post the summary if not cancelled.
            reportBlackboardPostCount(context.getJobId(), !context.fileIngestIsCancelled());
        }
    }

    synchronized static void addToBlackboardPostCount(long ingestJobId, long countToAdd) {
        Long fileCount = artifactCountsForIngestJobs.get(ingestJobId);
        if (fileCount == null) {
            fileCount = 0L;
        }
        artifactCountsForIngestJobs.put(ingestJobId, fileCount + countToAdd);
    }

    synchronized static void reportBlackboardPostCount(long ingestJobId, boolean postMessage) {
        Long refCount = refCounter.decrementAndGet(ingestJobId);
        if (refCount == 0) {
            Long filesCount = artifactCountsForIngestJobs.remove(ingestJobId);
            if (postMessage) {
                String msgText = String.format("Posted %d times to the blackboard",
                        filesCount == null ? 0L : filesCount);
                IngestServices.getInstance().postMessage(IngestMessage.createMessage(
                        IngestMessage.MessageType.INFO, MODULE_NAME, msgText));
            }
        }
    }

    /**
     * Decides whether to attempt a scan: the file must be at least minSize
     * bytes and either be thumbnail-capable (if enabled in the settings) or
     * start with a known image signature.
     */
    private boolean isImageFile(AbstractFile file) {
        if (file.getSize() < minSize) {
            return false;
        }
        if (useThumbnail && ImageUtils.isImageThumbnailSupported(file)) {
            return true;
        }

        byte[] header = new byte[HEADER_BYTES];
        int bytesRead;
        try {
            bytesRead = file.read(header, 0, HEADER_BYTES);
        } catch (TskCoreException ex) {
            return false; // can't read the first few bytes: don't parse
        }
        if (bytesRead <= 0) {
            return false;
        }

        // See http://www.garykessler.net/library/file_sigs.html
        return startsWith(header, bytesRead, 0xFF, 0xD8, 0xFF)                              // JPEG
                || startsWith(header, bytesRead, 0x42, 0x4D)                                // BMP "BM"
                || startsWith(header, bytesRead, 0x89, 0x50, 0x4E, 0x47)                    // PNG
                || startsWith(header, bytesRead, 0x47, 0x49, 0x46, 0x38)                    // GIF87a / GIF89a
                || startsWith(header, bytesRead, 0x49, 0x20, 0x49)                          // TIFF "I I"
                || startsWith(header, bytesRead, 0x49, 0x49, 0x2A, 0x00)                    // TIFF little endian
                || startsWith(header, bytesRead, 0x4D, 0x4D, 0x00, 0x2A)                    // TIFF big endian
                || startsWith(header, bytesRead, 0x4D, 0x4D, 0x00, 0x2B)                    // BigTIFF
                || startsWith(header, bytesRead, 0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20); // JPEG 2000 (JP2)
    }

    /** @return true if the first sig.length bytes read match the signature */
    private static boolean startsWith(byte[] buf, int bytesRead, int... sig) {
        if (bytesRead < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if ((buf[i] & 0xFF) != sig[i]) {
                return false;
            }
        }
        return true;
    }
}
