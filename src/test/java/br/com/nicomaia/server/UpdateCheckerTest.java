package br.com.nicomaia.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {

    @Test
    void shouldDetectNewerMajorVersion() {
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.0.0"));
    }

    @Test
    void shouldDetectNewerMinorVersion() {
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.0"));
    }

    @Test
    void shouldDetectNewerPatchVersion() {
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0.0"));
    }

    @Test
    void shouldNotDetectSameVersion() {
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"));
    }

    @Test
    void shouldNotDetectOlderVersion() {
        assertFalse(UpdateChecker.isNewer("1.0.0", "2.0.0"));
    }

    @Test
    void shouldHandleDifferentLengthVersions() {
        assertTrue(UpdateChecker.isNewer("1.0.0.1", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0.1"));
    }

    @Test
    void shouldHandleTwoPartVersions() {
        assertTrue(UpdateChecker.isNewer("1.1", "1.0"));
        assertFalse(UpdateChecker.isNewer("1.0", "1.1"));
    }
}
