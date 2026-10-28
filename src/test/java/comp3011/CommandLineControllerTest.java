/*
 * Authors:
 *   1. Raghav Sharma, a1907402
 *
 *assisted by Claude (Anthropic); reviewed and tested by the author.
 */
package comp3011;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandLineControllerTest {
    @TempDir
    Path tempDir;

    private String videoPath;

    @BeforeEach
    void createVideoFile() throws Exception {
        File video = Files.createFile(tempDir.resolve("movie.mp4")).toFile();
        videoPath = video.getPath();
    }

    private CommandLineController parse(String... args) {
        return new CommandLineController(args);
    }

    private List<Class<?>> classesOf(CommandLineController controller) {
        return controller.getFrameProcessors().stream().<Class<?>>map(FrameProcessor::getClass).toList();
    }

    @Test
    void stackedShortAndLongOptionsAreEquivalent() {
        List<Class<?>> expected = List.of(FrameJitterer.class, FrameFlickerer.class, FrameDuster.class,
                FrameDuster.class, FrameNumberer.class);

        assertEquals(expected, classesOf(parse("--jitter-frames", "--flicker-frames", "--dust-frames",
                "--dust-frames", "--number-frames", videoPath)));
        assertEquals(expected, classesOf(parse("-j", "-f", "-d", "-d", "-n", videoPath)));
        assertEquals(expected, classesOf(parse("-jfddn", videoPath)));
        assertEquals(expected, classesOf(parse("-jf", "--dust-frames", "-d", "--number-frames", videoPath)));
    }

    @Test
    void effectsAreAppliedInCommandLineOrderAndMayRepeat() {
        assertEquals(List.of(FrameNumberer.class, FrameScratcher.class, FrameScratcher.class, FrameNumberer.class),
                classesOf(parse("-nssn", videoPath)));
    }

    @Test
    void repeatedEffectsAreSeparateInstances() {
        List<FrameProcessor> processors = parse("-ss", videoPath).getFrameProcessors();
        assertNotSame(processors.get(0), processors.get(1));
    }

    @Test
    void generalOptionsCanBeStackedWithEffects() {
        CommandLineController controller = parse("-anx", videoPath);
        assertTrue(controller.isAudioRequested());
        assertTrue(controller.isMaximiseRequested());
        assertEquals(List.of(FrameNumberer.class), classesOf(controller));
        assertTrue(controller.shouldLaunchApplication());
    }

    @Test
    void unknownOptionIsAnError() {
        CommandLineController controller = parse("-sfbnz", videoPath);
        assertEquals(1, controller.getExitCode());
        assertTrue(!controller.shouldLaunchApplication());
    }

    @Test
    void missingVideoFileIsAnError() {
        assertEquals(1, parse("--number-frames").getExitCode());
    }

    @Test
    void twoVideoFilesIsAnError() {
        assertEquals(1, parse(videoPath, videoPath).getExitCode());
    }

    @Test
    void helpIsNotAnErrorAndSeparatesTheFooterParagraph() {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured));
        CommandLineController controller;
        try {
            controller = parse("--help");
        } finally {
            System.setOut(original);
        }

        assertEquals(0, controller.getExitCode());
        String help = captured.toString().replace("\r\n", "\n");
        assertTrue(help.contains("\nFrame processors:\n  -n, --number-frames    Render the frame number"));
        assertTrue(help.contains("blotches\n\nFrame processors are applied in command-line order"));
    }
}