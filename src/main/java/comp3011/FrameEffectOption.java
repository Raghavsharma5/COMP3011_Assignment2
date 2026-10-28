/*
 * Authors:
 *   1. Raghav Sharma, a1907402
 *
 *assisted by Claude (Anthropic); reviewed and tested by the author.
 */
package comp3011;

import java.util.function.Supplier;

/**
 * The single registry of command-line frame effects.
 *
 * <p>
 * To add a new effect: write a {@link FrameProcessor} subclass with a no-argument constructor, then add ONE constant
 * below with its short flag, long name, help text and constructor reference. {@link CommandLineController} parses
 * options and prints the help text from this enum, so nothing else needs editing. Each occurrence of an option calls
 * the factory again, so a repeated option such as {@code -ss} gets independent processor instances.
 * </p>
 */
public enum FrameEffectOption {
    NUMBER('n', "number-frames", "Render the frame number onto each frame", FrameNumberer::new),
    SCRATCH('s', "scratch-frames", "Render vertical film scratches", FrameScratcher::new),
    FLICKER('f', "flicker-frames", "Randomly dim frames", FrameFlickerer::new),
    BLACK_AND_WHITE('w', "black-and-white", "Convert frames to black and white", FrameBlackAndWhiter::new),
    YELLOW('y', "yellow-frames", "Apply a warmer colour temperature", FrameYellower::new),
    VIGNETTE('v', "vignette-frames", "Darken the frame edges", FrameVignetter::new),
    DUST('d', "dust-frames", "Render dust and hair marks", FrameDuster::new),
    JITTER('j', "jitter-frames", "Randomly displace frames by a few pixels", FrameJitterer::new),
    MOTTLE('m', "mottle-frames", "Add cloudy emulsion mottling", FrameMottler::new),
    BLEED('b', "bleed-frames", "Bleed light into frames", FrameBleeder::new),
    PEPPER('p', "pepper-frames", "Pepper frames with dark spots/blotches", FramePepperer::new);

    private final char shortFlag;
    private final String longName;
    private final String description;
    private final Supplier<FrameProcessor> factory;

    FrameEffectOption(char shortFlag, String longName, String description, Supplier<FrameProcessor> factory) {
        this.shortFlag = shortFlag;
        this.longName = longName;
        this.description = description;
        this.factory = factory;
    }

    public FrameProcessor create() {
        return factory.get();
    }

    public String helpLine() {
        return String.format("  %-23s%s", "-" + shortFlag + ", --" + longName, description);
    }

    public static FrameEffectOption fromArgument(String arg) {
        for (FrameEffectOption option : values()) {
            if (arg.equals("-" + option.shortFlag) || arg.equals("--" + option.longName)) {
                return option;
            }
        }
        return null;
    }
}