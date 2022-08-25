package com.didiglobal.booster.instrument;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebView;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.didiglobal.booster.instrument.Constants.TAG;
import static com.didiglobal.booster.instrument.Reflection.invokeStaticMethod;

/**
 * @author neighbWang
 */
public class ShadowWebView {

    public static void preloadWebView(final Application app) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
                        @Override
                        public boolean queueIdle() {
                            startChromiumEngine(app);
                            return false;
                        }
                    });
                } catch (final Throwable t) {
                    Log.e(TAG, "Oops!", t);
                }
            }
        });
    }

    private static void startChromiumEngine(final Context context) {
        try {
            final long t0 = SystemClock.uptimeMillis();
            final Object provider = invokeStaticMethod(Class.forName("android.webkit.WebViewFactory"), "getProvider");

            try {
                final Method startYourEngines = provider.getClass().getDeclaredMethod("startYourEngines", boolean.class);
                startYourEngines.setAccessible(true);
                startYourEngines.invoke(provider, true);
                Log.i(TAG, "Start chromium engine complete: " + (SystemClock.uptimeMillis() - t0) + " ms");
            } catch (final Throwable t) {
                final Set<Method> candidates = new HashSet<>();
                final Method[] methods = provider.getClass().getSuperclass().getDeclaredMethods();

                for (final Method m : methods) {
                    if (0 == (Modifier.STATIC & m.getModifiers())
                            && 1 == m.getParameterCount()
                            && void.class.equals(m.getReturnType())
                            && boolean.class.equals(m.getParameterTypes()[0])) {
                        candidates.add(m);
                    }
                }

                if (candidates.size() == 1) {
                    final Method startYourEngines = candidates.iterator().next();
                    startYourEngines.setAccessible(true);
                    startYourEngines.invoke(provider, true);
                    Log.i(TAG, "Start chromium engine from " + provider.getClass().getName() + "." + startYourEngines.getName() + "(boolean) complete: " + (SystemClock.uptimeMillis() - t0) + " ms");
                } else if (candidates.size() > 1) {
                    Log.w(TAG, "Method " + provider.getClass().getName() + ".startYourEngines(boolean) not found, multiple candidates found: " + Arrays.toString(candidates.toArray()));
                } else {
                    Log.w(TAG, "Method " + provider.getClass().getName() + ".startYourEngines(boolean) not found", t);
                }
            }

            if (Build.VERSION.SDK_INT >= 28) {
                String processName = Application.getProcessName();
                String packageName = context.getPackageName();
                if (!packageName.equals(processName)) {
                    WebView.setDataDirectorySuffix(processName);
                }
            }
        } catch (final Throwable t) {
            Log.e(TAG, "Start chromium engine error", t);
        }
    }
}