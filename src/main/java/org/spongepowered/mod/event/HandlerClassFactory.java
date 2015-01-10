/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered.org <http://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.spongepowered.mod.event;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.objectweb.asm.*;
import org.spongepowered.api.util.event.Cancellable;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.*;

class HandlerClassFactory implements HandlerFactory {

    private final AtomicInteger index = new AtomicInteger();
    private final LocalClassLoader classLoader = new LocalClassLoader(HandlerClassFactory.class.getClassLoader());
    private final String targetPackage;
    private final LoadingCache<CacheKey, Class<?>> cache = CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .weakValues()
            .build(
                    new CacheLoader<CacheKey, Class<?>>() {
                        @Override
                        public Class<?> load(CacheKey key) {
                            return createClass(key.type, key.method, key.ignoreCancelled);
                        }
                    });

    /**
     * Creates a new class factory.
     *
     * <p>Different instances of this class should use different packages.</p>
     *
     * @param targetPackage The target package
     */
    public HandlerClassFactory(String targetPackage) {
        checkNotNull(targetPackage, "targetPackage");
        this.targetPackage = targetPackage;
    }

    @Override
    public Handler createHandler(Object object, Method method, boolean ignoreCancelled) {
        synchronized (cache) {
            CacheKey key = new CacheKey(object.getClass(), method, ignoreCancelled);
            try {
                return (Handler) cache.getUnchecked(key)
                        .getConstructor(Method.class, boolean.class)
                        .newInstance(object, method);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create a handler", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Handler> createClass(Class<?> type, Method method, boolean ignoreCancelled) {
        Class<?> eventClass = method.getParameterTypes()[0];
        String name = targetPackage + "." + eventClass.getSimpleName() + "Handler_" + type.getSimpleName() + "_" + method.getName() + index.incrementAndGet();
        byte[] bytes = generateClass(type, method, eventClass, ignoreCancelled, name);
        return (Class<? extends Handler>) classLoader.defineClass(name, bytes);
    }

    public byte[] generateClass(Class<?> objectClass, Method method, Class<?> eventClass, boolean ignoreCancelled, String className) {
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES | COMPUTE_MAXS);
        MethodVisitor mv;

        String internalName = className.replace(".", "/");
        String targetInternalName = Type.getInternalName(objectClass);

        cw.visit(Opcodes.V1_6, ACC_PUBLIC + ACC_SUPER, internalName, null, Type.getInternalName(MethodHandler.class), null);

        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(L" + targetInternalName + ";Ljava/lang/reflect/Method;)V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(MethodHandler.class), "<init>", "(Ljava/lang/Object;Ljava/lang/reflect/Method;)V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "handle", "(Lorg/spongepowered/api/util/event/Event;)V", null, new String[] {"java/lang/reflect/InvocationTargetException"});
            mv.visitCode();

            if (ignoreCancelled) {
                Label exit = new Label();
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(INSTANCEOF, Type.getInternalName(Cancellable.class));
                mv.visitJumpInsn(IFEQ, exit);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, Type.getInternalName(Cancellable.class));
                mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Cancellable.class), "isCancelled", "()Z", true);
                mv.visitJumpInsn(IFEQ, exit);
                mv.visitInsn(RETURN);
                mv.visitLabel(exit);
            }

            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, internalName, "getObject", "()Ljava/lang/Object;", false);
            mv.visitTypeInsn(CHECKCAST, targetInternalName);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(eventClass));
            mv.visitMethodInsn(INVOKEVIRTUAL, targetInternalName, method.getName(), "(L" + Type.getInternalName(eventClass) + ";)V", false);

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "hashCode", "()I", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(MethodHandler.class), "hashCode", "()I", false);
            mv.visitInsn(IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "equals", "(Ljava/lang/Object;)Z", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(MethodHandler.class), "equals", "(Ljava/lang/Object;)Z", false);
            mv.visitInsn(IRETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();
    }

    private static class LocalClassLoader extends ClassLoader {
        public LocalClassLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    private static class CacheKey {
        private final Class<?> type;
        private final Method method;
        private final boolean ignoreCancelled;

        private CacheKey(Class<?> type, Method method, boolean ignoreCancelled) {
            this.type = type;
            this.method = method;
            this.ignoreCancelled = ignoreCancelled;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CacheKey cacheKey = (CacheKey) o;

            if (ignoreCancelled != cacheKey.ignoreCancelled) return false;
            if (!method.equals(cacheKey.method)) return false;
            if (!type.equals(cacheKey.type)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + method.hashCode();
            result = 31 * result + (ignoreCancelled ? 1 : 0);
            return result;
        }
    }

}
