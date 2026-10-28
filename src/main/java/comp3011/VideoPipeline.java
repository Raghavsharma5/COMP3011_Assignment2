/*
 * Authors:
 *   Raghav SHarma
 *   a1907402
 *   assisted by Claude (Anthropic); reviewed and tested by the author.
 */
package comp3011;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.JavaFXFrameConverter;

import javafx.scene.image.Image;

/**
 * The multi-threaded decode, effect and convert pipeline.
 *
 * <p>
 * Threads: one decoder, one per {@link FrameProcessor}, one converter. They are linked by bounded blocking queues, and
 * each frame is owned by exactly one thread at a time, handed over through a queue. The queues provide the
 * happens-before edges, so no further locking is needed. The grabber, each processor and the converter are each used
 * by a single thread only. The consumer (the JavaFX thread) reads finished items with {@link #peek()} and
 * {@link #poll()}. A pipeline is single use: {@link #stop()} interrupts and joins every thread.
 * </p>
 */
public class VideoPipeline {
    private static final int STAGE_QUEUE_CAPACITY = 3;
    private static final int DISPLAY_QUEUE_CAPACITY = 8;

    private final List<Thread> threads = new ArrayList<>();
    private final BlockingQueue<PipelineItem> displayQueue = new ArrayBlockingQueue<>(DISPLAY_QUEUE_CAPACITY);

    public VideoPipeline(
            FFmpegFrameGrabber grabber,
            boolean audioAvailable,
            BiConsumer<Long, Frame> audioSink,
            Function<Frame, InfoVideo> videoInfoFactory,
            List<FrameProcessor> frameProcessors,
            JavaFXFrameConverter converter) {
        BlockingQueue<PipelineItem> input = new ArrayBlockingQueue<>(STAGE_QUEUE_CAPACITY);
        addThread("decoder",
                new DecoderTask(input, grabber, audioAvailable, audioSink, videoInfoFactory, frameProcessors));

        for (int i = 0; i < frameProcessors.size(); i++) {
            BlockingQueue<PipelineItem> output = new ArrayBlockingQueue<>(STAGE_QUEUE_CAPACITY);
            addThread("effect-" + i, new EffectTask(input, output, frameProcessors.get(i)));
            input = output;
        }

        addThread("converter", new ConverterTask(input, displayQueue, converter));
    }

    public void start() {
        threads.forEach(Thread::start);
    }

    public PipelineItem peek() {
        return displayQueue.peek();
    }

    public PipelineItem poll() {
        return displayQueue.poll();
    }

    public void stop() {
        threads.forEach(Thread::interrupt);
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        displayQueue.clear();
    }

    private void addThread(String name, Runnable task) {
        Thread thread = new Thread(task, "video-" + name);
        thread.setDaemon(true);
        threads.add(thread);
    }

    private abstract static class Task implements Runnable {
        protected final BlockingQueue<PipelineItem> output;

        Task(BlockingQueue<PipelineItem> output) {
            this.output = output;
        }

        @Override
        public final void run() {
            try {
                work();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                try {
                    output.put(PipelineItem.failure(e));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        protected abstract void work() throws Exception;
    }

    private static class DecoderTask extends Task {
        private final FFmpegFrameGrabber grabber;
        private final boolean audioAvailable;
        private final BiConsumer<Long, Frame> audioSink;
        private final Function<Frame, InfoVideo> videoInfoFactory;
        private final List<FrameProcessor> frameProcessors;

        DecoderTask(
                BlockingQueue<PipelineItem> output,
                FFmpegFrameGrabber grabber,
                boolean audioAvailable,
                BiConsumer<Long, Frame> audioSink,
                Function<Frame, InfoVideo> videoInfoFactory,
                List<FrameProcessor> frameProcessors) {
            super(output);
            this.grabber = grabber;
            this.audioAvailable = audioAvailable;
            this.audioSink = audioSink;
            this.videoInfoFactory = videoInfoFactory;
            this.frameProcessors = frameProcessors;
        }

        @Override
        protected void work() throws Exception {
            boolean initialised = false;
            while (true) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                Frame frame = audioAvailable ? grabber.grab() : grabber.grabImage();
                if (frame == null) {
                    output.put(PipelineItem.end());
                    return;
                }

                long timestampUs = grabber.getTimestamp();
                if (audioAvailable && frame.samples != null) {
                    audioSink.accept(timestampUs, frame);
                }

                if (frame.image == null) {
                    continue;
                }

                if (!initialised) {
                    InfoVideo videoInfo = videoInfoFactory.apply(frame);
                    for (FrameProcessor processor : frameProcessors) {
                        processor.initialise(videoInfo);
                    }
                    initialised = true;
                }

                output.put(PipelineItem.decoded(frame.clone(), grabber.getFrameNumber(), timestampUs));
            }
        }
    }

    private static class EffectTask extends Task {
        private final BlockingQueue<PipelineItem> input;
        private final FrameProcessor processor;

        EffectTask(BlockingQueue<PipelineItem> input, BlockingQueue<PipelineItem> output, FrameProcessor processor) {
            super(output);
            this.input = input;
            this.processor = processor;
        }

        @Override
        protected void work() throws Exception {
            while (true) {
                PipelineItem item = input.take();
                if (item.kind() == PipelineItem.Kind.FRAME) {
                    processor.process(item.frame(), new InfoFrame(item.frameNumber(), item.timestampUs()));
                }
                output.put(item);
                if (item.kind() != PipelineItem.Kind.FRAME) {
                    return;
                }
            }
        }
    }

    private static class ConverterTask extends Task {
        private final BlockingQueue<PipelineItem> input;
        private final JavaFXFrameConverter converter;

        ConverterTask(BlockingQueue<PipelineItem> input, BlockingQueue<PipelineItem> output,
                JavaFXFrameConverter converter) {
            super(output);
            this.input = input;
            this.converter = converter;
        }

        @Override
        protected void work() throws Exception {
            while (true) {
                PipelineItem item = input.take();
                if (item.kind() != PipelineItem.Kind.FRAME) {
                    output.put(item);
                    return;
                }
                Image image = converter.convert(item.frame());
                output.put(item.converted(image));
            }
        }
    }
}