/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.jfr.consumer;

import java.io.IOException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.EventSettings;
import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.internal.PlatformRecording;
import jdk.jfr.internal.PrivateAccess;
import jdk.jfr.internal.SecuritySupport;
import jdk.jfr.internal.Utils;
import jdk.jfr.internal.consumer.EventDirectoryStream;

/**
 * A recording stream produces events from the current JVM (Java Virtual
 * Machine).
 * <p>
 * The following example shows how to record events using the default
 * configuration and print the Garbage Collection, CPU Load and JVM Information
 * event to standard out.
 * <pre>
 * <code>
 * Configuration c = Configuration.getConfiguration("default");
 * try (var rs = new RecordingStream(c)) {
 *     rs.onEvent("jdk.GarbageCollection", System.out::println);
 *     rs.onEvent("jdk.CPULoad", System.out::println);
 *     rs.onEvent("jdk.JVMInformation", System.out::println);
 *     rs.start();
 *   }
 * }
 * </code>
 * </pre>
 *
 * @since 14
 */
@Deprecated
public final class RecordingStream implements AutoCloseable, EventStream {

    private final Recording recording;
    private final EventDirectoryStream directoryStream;

    /**
     * Creates an event stream for the current JVM (Java Virtual Machine).
     *
     * @throws IllegalStateException if Flight Recorder can't be created (for
     *         example, if the Java Virtual Machine (JVM) lacks Flight Recorder
     *         support, or if the file repository can't be created or accessed)
     *
     * @throws SecurityException if a security manager exists and the caller
     *         does not have
     *         {@code FlightRecorderPermission("accessFlightRecorder")}
     */
    public RecordingStream() {
        Utils.checkAccessFlightRecorder();
        AccessControlContext acc = AccessController.getContext();
        this.recording = new Recording();
        try {
            PlatformRecording pr = PrivateAccess.getInstance().getPlatformRecording(recording);
            this.directoryStream = new EventDirectoryStream(acc, null, SecuritySupport.PRIVILIGED, pr);
        } catch (IOException ioe) {
            this.recording.close();
            throw new IllegalStateException(ioe.getMessage());
        }
    }

    /**
     * Creates a recording stream using settings from a configuration.
     * <p>
     * The following example shows how to create a recording stream that uses a
     * predefined configuration.
     *
     * <pre>
     * <code>
     * var c = Configuration.getConfiguration("default");
     * try (var rs = new RecordingStream(c)) {
     *   rs.onEvent(System.out::println);
     *   rs.start();
     * }
     * </code>
     * </pre>
     *
     * @param configuration configuration that contains the settings to use,
     *        not {@code null}
     *
     * @throws IllegalStateException if Flight Recorder can't be created (for
     *         example, if the Java Virtual Machine (JVM) lacks Flight Recorder
     *         support, or if the file repository can't be created or accessed)
     *
     * @throws SecurityException if a security manager is used and
     *         FlightRecorderPermission "accessFlightRecorder" is not set.
     *
     * @see Configuration
     */
    public RecordingStream(Configuration configuration) {
        this();
        recording.setSettings(configuration.getSettings());
    }

    /**
     * Enables the event with the specified name.
     * <p>
     * If multiple events have the same name (for example, the same class is
     * loaded in different class loaders), then all events that match the name
     * are enabled. To enable a specific class, use the {@link #enable(Class)}
     * method or a {@code String} representation of the event type ID.
     *
     * @param name the settings for the event, not {@code null}
     *
     * @return an event setting for further configuration, not {@code null}
     *
     * @see EventType
     */
    public EventSettings enable(String name) {
        return recording.enable(name);
    }

    /**
     * Replaces all settings for this recording stream.
     * <p>
     * The following example records 20 seconds using the "default" configuration
     * and then changes settings to the "profile" configuration.
     *
     * <pre>
     * <code>
     *     Configuration defaultConfiguration = Configuration.getConfiguration("default");
     *     Configuration profileConfiguration = Configuration.getConfiguration("profile");
     *     try (var rs = new RecordingStream(defaultConfiguration) {
     *        rs.onEvent(System.out::println);
     *        rs.startAsync();
     *        Thread.sleep(20_000);
     *        rs.setSettings(profileConfiguration.getSettings());
     *        Thread.sleep(20_000);
     *     }
     * </code>
     * </pre>
     *
     * @param settings the settings to set, not {@code null}
     *
     * @see Recording#setSettings(Map)
     */
    public void setSettings(Map<String, String> settings) {
        recording.setSettings(settings);
    };

    /**
     * Enables event.
     *
     * @param eventClass the event to enable, not {@code null}
     *
     * @throws IllegalArgumentException if {@code eventClass} is an abstract
     *         class or not a subclass of {@link Event}
     *
     * @return an event setting for further configuration, not {@code null}
     */
    public EventSettings enable(Class<? extends Event> eventClass) {
        return recording.enable(eventClass);
    }

    /**
     * Disables event with the specified name.
     * <p>
     * If multiple events with same name (for example, the same class is loaded
     * in different class loaders), then all events that match the name are
     * disabled. To disable a specific class, use the {@link #disable(Class)}
     * method or a {@code String} representation of the event type ID.
     *
     * @param name the settings for the event, not {@code null}
     *
     * @return an event setting for further configuration, not {@code null}
     *
     */
    public EventSettings disable(String name) {
        return recording.disable(name);
    }

    /**
     * Disables event.
     *
     * @param eventClass the event to enable, not {@code null}
     *
     * @throws IllegalArgumentException if {@code eventClass} is an abstract
     *         class or not a subclass of {@link Event}
     *
     * @return an event setting for further configuration, not {@code null}
     *
     */
    public EventSettings disable(Class<? extends Event> eventClass) {
        return recording.disable(eventClass);
    }

    /**
     * Determines how far back data is kept for the stream.
     * <p>
     * To control the amount of recording data stored on disk, the maximum
     * length of time to retain the data can be specified. Data stored on disk
     * that is older than the specified length of time is removed by the Java
     * Virtual Machine (JVM).
     * <p>
     * If neither maximum limit or the maximum age is set, the size of the
     * recording may grow indefinitely if events are on
     *
     * @param maxAge the length of time that data is kept, or {@code null} if
     *        infinite
     *
     * @throws IllegalArgumentException if {@code maxAge} is negative
     *
     * @throws IllegalStateException if the recording is in the {@code CLOSED}
     *         state
     */
    public void setMaxAge(Duration maxAge) {
        recording.setMaxAge(maxAge);
    }

    /**
     * Determines how much data is kept for the stream.
     * <p>
     * To control the amount of recording data that is stored on disk, the
     * maximum amount of data to retain can be specified. When the maximum limit
     * is exceeded, the Java Virtual Machine (JVM) removes the oldest chunk to
     * make room for a more recent chunk.
     * <p>
     * If neither maximum limit or the maximum age is set, the size of the
     * recording may grow indefinitely.
     * <p>
     * The size is measured in bytes.
     *
     * @param maxSize the amount of data to retain, {@code 0} if infinite
     *
     * @throws IllegalArgumentException if {@code maxSize} is negative
     *
     * @throws IllegalStateException if the recording is in {@code CLOSED} state
     */
    public void setMaxSize(long maxSize) {
        recording.setMaxSize(maxSize);
    }

    @Override
    public void setReuse(boolean reuse) {
        directoryStream.setReuse(reuse);
    }

    @Override
    public void setOrdered(boolean ordered) {
        directoryStream.setOrdered(ordered);
    }

    @Override
    public void setStartTime(Instant startTime) {
        directoryStream.setStartTime(startTime);
    }

    @Override
    public void setEndTime(Instant endTime) {
        directoryStream.setEndTime(endTime);
    }

    @Override
    public void onEvent(String eventName, Consumer<RecordedEvent> action) {
        directoryStream.onEvent(eventName, action);
    }

    @Override
    public void onEvent(Consumer<RecordedEvent> action) {
        directoryStream.onEvent(action);
    }

    @Override
    public void onFlush(Runnable action) {
        directoryStream.onFlush(action);
    }

    @Override
    public void onClose(Runnable action) {
        directoryStream.onClose(action);
    }

    @Override
    public void onError(Consumer<Throwable> action) {
        directoryStream.onError(action);
    }

    @Override
    public void close() {
        recording.close();
        directoryStream.close();
    }

    @Override
    public boolean remove(Object action) {
        return directoryStream.remove(action);
    }

    @Override
    public void start() {
        PlatformRecording pr = PrivateAccess.getInstance().getPlatformRecording(recording);
        long startNanos = pr.start();
        directoryStream.start(startNanos);
    }

    @Override
    public void startAsync() {
        PlatformRecording pr = PrivateAccess.getInstance().getPlatformRecording(recording);
        long startNanos = pr.start();
        directoryStream.startAsync(startNanos);
    }

    @Override
    public void awaitTermination(Duration timeout) throws InterruptedException {
        directoryStream.awaitTermination(timeout);
    }

    @Override
    public void awaitTermination() throws InterruptedException {
        directoryStream.awaitTermination();
    }
}
