package com.elytra.client.gui.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

/**
 * Saves and restores the OpenGL state that the ImGui GL3 backend touches.
 *
 * Minecraft's Blaze3D layer keeps its own shadow copy of some GL state. Drawing
 * ImGui behind its back and leaving the pipeline altered would desynchronise that
 * copy, so everything we can plausibly disturb is captured before the draw and put
 * back afterwards, byte for byte.
 */
final class GlStateSnapshot {

    private int drawFramebuffer;
    private int readFramebuffer;
    private int program;
    private int vertexArray;
    private int arrayBuffer;
    private int activeTexture;
    private int texture2D;
    private int sampler;

    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];

    private boolean scissorTest;
    private boolean blend;
    private boolean depthTest;
    private boolean cullFace;

    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;

    void capture() {
        drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = GL11.glGetInteger(GL20.GL_ARRAY_BUFFER_BINDING);
        activeTexture = GL11.glGetInteger(GL20.GL_ACTIVE_TEXTURE);
        texture2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, buffer);
            buffer.get(viewport);
            buffer.clear();
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, buffer);
            buffer.get(scissorBox);
        }

        scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    }

    void restore() {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vertexArray);
        GL20.glBindBuffer(GL20.GL_ARRAY_BUFFER, arrayBuffer);
        GL13.glActiveTexture(activeTexture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2D);
        GL33.glBindSampler(0, sampler);

        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);

        setCapability(GL11.GL_SCISSOR_TEST, scissorTest);
        setCapability(GL11.GL_BLEND, blend);
        setCapability(GL11.GL_DEPTH_TEST, depthTest);
        setCapability(GL11.GL_CULL_FACE, cullFace);

        GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
    }

    private static void setCapability(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }
}
