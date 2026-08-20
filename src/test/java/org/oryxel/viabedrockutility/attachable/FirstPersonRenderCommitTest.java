package org.oryxel.viabedrockutility.attachable;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstPersonRenderCommitTest {
    @Test
    void standardAttachableProfileKeepsHostMeshHidden() {
        assertEquals(FirstPersonHostMeshPolicy.HIDDEN,
                BedrockFirstPersonView.STANDARD.hostMeshPolicy());
    }

    @Test
    void hiddenPolicyCommitsWithoutSubmittingHostGeometry() {
        final AtomicInteger submissions = new AtomicInteger();
        final FirstPersonRenderCommit commit = new FirstPersonRenderCommit(
                FirstPersonHostMeshPolicy.HIDDEN, submissions::incrementAndGet);

        commit.commit();
        commit.commit();

        assertTrue(commit.committed());
        assertEquals(0, submissions.get());
    }

    @Test
    void boundArmPolicySubmitsAtMostOnce() {
        final AtomicInteger submissions = new AtomicInteger();
        final FirstPersonRenderCommit commit = new FirstPersonRenderCommit(
                FirstPersonHostMeshPolicy.BOUND_ARM, submissions::incrementAndGet);

        commit.commit();
        commit.commit();

        assertEquals(1, submissions.get());
    }

    @Test
    void rejectedAttachableDoesNotCommitHostGeometry() {
        final AtomicInteger submissions = new AtomicInteger();
        final FirstPersonRenderCommit commit = new FirstPersonRenderCommit(
                FirstPersonHostMeshPolicy.BOUND_ARM, submissions::incrementAndGet);

        assertFalse(commit.committed());
        assertEquals(0, submissions.get());
    }
}
