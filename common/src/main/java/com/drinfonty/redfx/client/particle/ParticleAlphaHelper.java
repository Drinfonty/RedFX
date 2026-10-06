package com.drinfonty.redfx.client.particle;

import net.minecraft.client.particle.Particle;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ParticleAlphaHelper {
    private static final Map<Class<?>, MethodHandle> SET_ALPHA_HANDLES = new ConcurrentHashMap<>();
    private static final MethodHandle NOOP;

    static {
        MethodHandle noop = null;
        try {
            noop = MethodHandles.lookup().findStatic(ParticleAlphaHelper.class, "noop",
                java.lang.invoke.MethodType.methodType(void.class, Particle.class, float.class));
        } catch (Throwable ignored) {
        }
        NOOP = noop;
    }

    private ParticleAlphaHelper() {}

    private static void noop(Particle p, float alpha) {}

    public static void setAlpha(Particle particle, float alpha) {
        if (particle == null) return;
        Class<?> clazz = particle.getClass();
        MethodHandle handle = SET_ALPHA_HANDLES.computeIfAbsent(clazz, ParticleAlphaHelper::findSetAlphaHandle);
        try {
            handle.invokeExact(particle, alpha);
        } catch (Throwable ignored) {
        }
    }

    private static MethodHandle findSetAlphaHandle(Class<?> clazz) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getReturnType() == void.class && m.getParameterCount() == 1 && m.getParameterTypes()[0] == float.class) {
                    try {
                        m.setAccessible(true);
                        return lookup.unreflect(m);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return NOOP;
    }
}
