/*
 * Authors:
 *   Raghav Sharma
 *   a1907402
 *   assisted by Claude (Anthropic); reviewed and tested by the author.
 */
package comp3011;

import org.bytedeco.javacv.Frame;

import javafx.scene.image.Image;

public record PipelineItem(
        Kind kind,
        Frame frame,
        Image image,
        int frameNumber,
        long timestampUs,
        long readyNs,
        Exception error) {

    public enum Kind {
        FRAME, END, FAILURE
    }

    public static PipelineItem decoded(Frame frame, int frameNumber, long timestampUs) {
        return new PipelineItem(Kind.FRAME, frame, null, frameNumber, timestampUs, 0, null);
    }

    public static PipelineItem end() {
        return new PipelineItem(Kind.END, null, null, 0, 0, 0, null);
    }

    public static PipelineItem failure(Exception error) {
        return new PipelineItem(Kind.FAILURE, null, null, 0, 0, 0, error);
    }

    public PipelineItem converted(Image image) {
        return new PipelineItem(kind, null, image, frameNumber, timestampUs, System.nanoTime(), null);
    }
}