# COMP3011 Assignment 2: Multi-threaded Film-Effect Video Player

Author: Raghav Sharma, a1907402

Usage: `mvn javafx:run '-Djavafx.args=-a -nssfwyvdjmbp /path/to/video.mp4'` (see `--help` for all options).
Tests: `mvn verify`.

## Command line (Part 1)
`CommandLineController` expands stacked short options (`-jfddn` becomes `-j -f -d -d -n`), looks effects up in
`FrameEffectOption` and builds the `FrameProcessor` list in command-line order. The help text is generated from the
same enum, so the parser and help cannot drift apart. To add an effect, add one enum constant.
`VideoPlayerModel` receives the list and never creates effects itself.

## Threading architecture (Part 2)
decoder thread -> effect thread 1 -> ... -> effect thread N -> converter thread -> display queue -> JavaFX timer

* **Decoder** (`VideoPipeline.DecoderTask`): the only thread that touches the FFmpeg grabber. It queues audio, clones
  each video frame (the grabber reuses its buffers) and passes it on.
* **One thread per effect** (`EffectTask`): each `FrameProcessor` is used by exactly one thread, so stateful effects
  need no synchronisation. Queues are FIFO, so effect order and frame order are preserved.
* **Converter** (`ConverterTask`): turns the processed frame into a JavaFX `Image`.
* **JavaFX thread** (`VideoPlayerModel`): owns the playback clock, pause state, audio output and frame display. It
  only takes finished frames from the display queue and shows them when due. No decoding or effects run on it, so
  the GUI stays responsive during resize and drag.

## Why it is free of race conditions
* **Ownership transfer.** A frame is held by exactly one thread at a time and moves between threads only through
  bounded `ArrayBlockingQueue`s. `put`/`take` give the happens-before edges, so there is no shared mutable frame and
  no tearing or effects overwriting each other.
* **Shared state is minimal.** The only state shared with the JavaFX thread is the display queue and the
  `ConcurrentLinkedQueue` of pending audio. Playback clock and pause fields are touched by the JavaFX thread only.
  Fields read by pipeline threads (grabber, audio player, frame rate) are set before the threads start
  (`Thread.start` happens-before) and are not changed until `stop()` has joined them.
* **Back-pressure and no deadlock.** Queues are bounded, so a slow stage slows upstream stages instead of growing
  memory. Every blocking `put`/`take` is interruptible, so `stop()` interrupts all threads and joins them without
  deadlock.
* **Seek, stop, end and errors.** A seek stops and joins the old pipeline, repositions the grabber, resets the
  clock and starts a new pipeline, so stale frames cannot be displayed. End of stream and failures travel down the
  pipeline as `END` and `FAILURE` items, so the JavaFX thread handles them in order.
* **Trade-off.** Throughput is limited by the slowest stage, and one thread per effect means many threads on few
  cores at high effect counts. TITAN (8 vCPU): real time at 1, 11 and 21 effects, frames dropped at 41.

## Tests
`CommandLineControllerTest` covers stacking/mixing, ordering, repeats, independent instances, error cases and the
exact help-text paragraph separation.

## Acknowledgement
Parts of the design and code were developed with assistance from Claude (Anthropic). I reviewed and tested the
code and can explain it.
