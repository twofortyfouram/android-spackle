/*
 * android-spackle https://github.com/twofortyfouram/android-spackle
 * Copyright (C) 2009–2017 two forty four a.m. LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.twofortyfouram.log;

import android.annotation.TargetApi;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.twofortyfouram.spackle.AndroidSdkVersion;
import com.twofortyfouram.spackle.AppBuildInfo;
import com.twofortyfouram.spackle.ContextUtil;
import com.twofortyfouram.spackle.ProcessUtil;
import com.twofortyfouram.spackle.R;
import com.twofortyfouram.spackle.ResourceUtil;
import com.twofortyfouram.spackle.bundle.BundlePrinter;
import com.twofortyfouram.spackle.internal.Reflector;

import net.jcip.annotations.ThreadSafe;

import java.util.Arrays;
import java.util.Locale;

import static com.twofortyfouram.assertion.Assertions.assertNotNull;

/**
 * Brawny and flannel-clad indeed, this class takes care of all your logging needs.
 * <p>
 * Going beyond {@link Log}, this class automatically generates a log tag, prefixes
 * log messages with the current process and thread name, the current class and method
 * name, and handles String formats with ease. When given certain objects as String formats, such
 * as Cursor, Array, Intent, or Bundle, this class knows how to stringify them in a useful manner
 * instead of using the native toString() methods of these objects.
 * <p>
 * Prior to calling any of the logging methods of this class, clients should call
 * {@link #init(Context)}. Typically this call is made from either
 * {@link android.app.Application#onCreate()} or {@link android.content.ContentProvider#onCreate()}.
 * After {@link #init(Context)} is called, the log tag is automatically generated by lowercasing
 * the app's name and replacing whitespace with hyphens. Clients may override this behavior, by
 * adding the string resource {@code com_twofortyfouram_log_tag} to set an explicit log tag.
 * <p>
 * Because logging in production code is literally dead wood, clients should strip out logging
 * statements at compile time with ProGuard/DexGuard, with the exception of the
 * {@link #always(String, Object...)}.  The necessary configuration is bundled with the spackle AAR.
 *
 * Note that prefixing of process, thread, class, and method is disabled on release builds,
 * as classes will probably be obfuscated and thus potentially concerning to users who look at
 * log messages on release builds.  This can be manually configured by setting a boolean resource
 * "com_twofortyfouram_log_is_debug".
 */
/*
 * Printing the process and thread name in logcat is incredibly valuable, as this has helped two
 * forty four a.m. identify dozens of threading bugs in Android over the years.
 *
 * By design, this class is not intended to be maximally efficient.  This class is for debug only
 * and logs really should be stripped out with ProGuard.
 *
 * Yes, the name Lumberjack is ridiculous. But all the cool kids already have names like {@link
 * java.util.logging.LogManager}, {@link java.util.logging.Logger}, {@link android.util.Log}.
 * This class didn't want to seem like a poser.
 */
@ThreadSafe
public final class Lumberjack {

    @NonNull
    private static final String BOOLEAN_RESOURCE_IS_DEBUG = "com_twofortyfouram_log_is_debug";
    //$NON-NLS

    /**
     * Format string for log messages.
     * <p>
     * The format is: <Process> <Thread> <Class>.<method>(): <message>
     */
    @NonNull
    private static final String FORMAT = "%-33s %-30s %s.%s(): %s"; //$NON-NLS-1$

    /**
     * Log level for always.
     */
    private static final int ALWAYS = 0;

    /**
     * Params used for reflection invocation.
     */
    @NonNull
    private static final Class<?>[] LOG_PARAMS = new Class<?>[]{
            String.class, String.class
    };

    /**
     * Log tag for use with {@link Log}.
     */
    @SuppressWarnings("StaticNonFinalField")
    @NonNull
    private static volatile String sLogTag = "Lumberjack"; //$NON-NLS-1$

    /**
     * Process name.
     */
    @SuppressWarnings("StaticNonFinalField")
    @NonNull
    private static volatile String sProcessName = ""; //$NON-NLS-1$

    /**
     * Flag indicating whether extra debug options are enabled.
     */
    @SuppressWarnings("StaticNonFinalField")
    private static volatile boolean sIsDebuggable = false;

    /**
     * Initializes logging.
     * <p>
     * This normally should be called once in {@link android.app.Application#onCreate()}. (It may
     * also need to be called from {@link android.content.ContentProvider#onCreate()} due to the
     * fact that ContentProvider objects are created before Application objects on some Android
     * platform versions).
     *
     * @param context Application context.
     */
    public static void init(@NonNull final Context context) {
        final Context ctx = ContextUtil.cleanContext(context);

        sLogTag = getLogTag(ctx);
        sProcessName = ProcessUtil.getProcessName(ctx);

        try {
            sIsDebuggable = ResourceUtil.getBoolean(ctx, BOOLEAN_RESOURCE_IS_DEBUG);
        } catch (final Resources.NotFoundException e) {
            sIsDebuggable = AppBuildInfo.isDebuggable(ctx);
        }
    }

    public static void enableFragmentAndLoaderLogging() {
        enableFragmentAndLoaderLoggingSupport();

        if (AndroidSdkVersion.isAtLeastSdk(Build.VERSION_CODES.HONEYCOMB)) {
            enableFragmentAndLoaderLoggingHoneycomb();
        }
    }

    private static void enableFragmentAndLoaderLoggingSupport() {
        /*
         * In debug builds, the support library should not be obfuscated so this reflection call
         * will succeed if the support library is present.  Reflection is used so that this
         * library doesn't directly depend on the support library.
         */
        try {
            final Class<?>[] types = new Class<?>[]{
                    Boolean.TYPE
            };
            final Boolean[] params = {
                    Boolean.TRUE
            };

            Reflector.tryInvokeStatic("android.support.v4.app.FragmentManager", //$NON-NLS-1$
                    "enableDebugLogging", types, params); //$NON-NLS-1$
            Reflector.tryInvokeStatic("android.support.v4.app.LoaderManager", //$NON-NLS-1$
                    "enableDebugLogging", types, params); //$NON-NLS-1$
        } catch (final RuntimeException e) {
            // Do nothing
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void enableFragmentAndLoaderLoggingHoneycomb() {
        FragmentManager.enableDebugLogging(true);
        LoaderManager.enableDebugLogging(true);
    }

    /**
     * Always log a message, because this method is not stripped out by ProGuard/DexGuard by
     * default. Messages from this method are logged with the {@link Log#INFO} logging
     * level.
     * <p>
     * This method should be used with discretion, as log statements will be preserved in release
     * builds.  DO NOT log sensitive information, such as password, access tokens, location
     * information, or other private details that shouldn't be readable by other applications.
     *
     * @param msg  message to log.
     * @param args to format into the String.
     */
    public static void always(@NonNull final String msg, @Nullable final Object... args) {
        logMessage(ALWAYS, msg, args);
    }

    /**
     * Log a message.
     *
     * @param msg  message to log. This message is expected to be a format string if varargs are
     *             passed.
     * @param args optional arguments to be formatted into {@code msg}.
     */
    public static void v(@NonNull final String msg, @Nullable final Object... args) {
        logMessage(Log.VERBOSE, msg, args);
    }

    /**
     * Log a message.
     *
     * @param msg  message to log. This message is expected to be a format string if varargs are
     *             passed.
     * @param args optional arguments to be formatted into {@code msg}.
     */
    public static void d(@NonNull final String msg, final Object... args) {
        logMessage(Log.DEBUG, msg, args);
    }

    /**
     * Log a message.
     *
     * @param msg  message to log. This message is expected to be a format string if varargs are
     *             passed.
     * @param args optional arguments to be formatted into {@code msg}.
     */
    public static void i(@NonNull final String msg, @Nullable final Object... args) {
        logMessage(Log.INFO, msg, args);
    }

    /**
     * Log a message.
     *
     * @param msg  message to log. This message is expected to be a format string if varargs are
     *             passed.
     * @param args optional arguments to be formatted into {@code msg}.
     */
    public static void w(@NonNull final String msg, @Nullable final Object... args) {
        logMessage(Log.WARN, msg, args);
    }

    /**
     * Log a message.
     *
     * @param msg  message to log. This message is expected to be a format string if varargs are
     *             passed.
     * @param args optional arguments to be formatted into {@code msg}.
     */
    public static void e(@NonNull final String msg, @Nullable final Object... args) {
        logMessage(Log.ERROR, msg, args);
    }

    /**
     * Helper for varargs.
     *
     * @param msg  The format string.
     * @param args The format arguments.  Note: this parameter will be mutated.  If a varags call,
     *             this won't matter.
     * @return A string formatted with the arguments
     */
    @NonNull
    public static String formatMessage(@NonNull final String msg, @Nullable final Object... args) {
        assertNotNull(msg, "msg"); //$NON-NLS-1$

        // It is OK to mutate the input parameter
        if (null != args) {
            for (int x = 0; x < args.length; x++) {
                args[x] = formatObject(args[x]);
            }
        }

        return String.format(Locale.US, msg, args);
    }

    @Nullable
    private static Object formatObject(@Nullable final Object object) {
        final Object result;

        if (null == object) {
            result = null;
        } else if (object instanceof Throwable) {
            result = formatError((Throwable) object);
        } else if (object instanceof Intent) {
            result = formatIntent((Intent) object);
        } else if (object instanceof Bundle) {
            result = formatBundle((Bundle) object);
        } else if (object instanceof Cursor) {
            result = formatCursor((Cursor) object);
        } else if (object.getClass().isArray()) {
            result = formatArray(object);
        } else {
            result = object;
        }

        return result;
    }

    @NonNull
    private static String formatArray(@NonNull final Object array) {
        final String result;
        final Class<?> cls = array.getClass();

        if (cls.getComponentType().isPrimitive()) {
            result = Reflector.tryInvokeStatic(Arrays.class,
                    "toString", new Class<?>[]{cls}, new Object[]{array}); //$NON-NLS-1$
        } else {
            result = Reflector.tryInvokeStatic(Arrays.class,
                    "deepToString", new Class<?>[]{Object[].class},
                    new Object[]{array}); //$NON-NLS-1$
        }

        return result;
    }

    @NonNull
    private static String formatError(@NonNull final Throwable error) {
        return String.format(Locale.US, "\n%s", Log.getStackTraceString(error)); //$NON-NLS-1$
    }

    @NonNull
    private static String formatBundle(@NonNull final Bundle bundle) {
        return BundlePrinter.toString(bundle);
    }

    @NonNull
    private static String formatIntent(@NonNull final Intent intent) {
        return String.format(Locale.US,
                "%s with extras %s", intent,
                BundlePrinter.toString(intent.getExtras())); //$NON-NLS-1$
    }

    @NonNull
    private static String formatCursor(@NonNull final Cursor cursor) {
        return DatabaseUtils.dumpCursorToString(cursor);
    }

    private static void logMessage(final int logLevel, final String message,
            @Nullable final Object[] messageFormatArgs) {
        final String formattedMessage = formatMessage(message, messageFormatArgs);

        final String logatLogLine;
        if (sIsDebuggable) {
            // new Throwable().getStacktrace() is faster than Thread.currentThread().getStackTrace()
            final StackTraceElement[] trace = new Throwable().getStackTrace();
            final String sourceClass = trace[2].getClassName();
            final String sourceMethod = trace[2].getMethodName();

            logatLogLine = String
                    .format(Locale.US, FORMAT, sProcessName, Thread.currentThread().getName(),
                            sourceClass, sourceMethod, formattedMessage);
        } else {
            logatLogLine = formattedMessage;
        }

        switch (logLevel) {
            case ALWAYS: {
                /*
                 * Use reflection to prevent ProGuard/DexGuard from stripping out always log
                 * statements.
                 */
                final Object[] params = {
                        sLogTag, logatLogLine
                };
                Reflector.tryInvokeStatic(Log.class, "i", LOG_PARAMS, params);//$NON-NLS-1$
                break;
            }
            case Log.VERBOSE: {
                Log.v(sLogTag, logatLogLine);
                break;
            }
            case Log.DEBUG: {
                Log.d(sLogTag, logatLogLine);
                break;
            }
            case Log.INFO: {
                Log.i(sLogTag, logatLogLine);
                break;
            }
            case Log.WARN: {
                Log.w(sLogTag, logatLogLine);
                break;
            }
            case Log.ERROR: {
                Log.e(sLogTag, logatLogLine);
                break;
            }
            case Log.ASSERT: {
                Log.wtf(sLogTag, logatLogLine);
                break;
            }
            default: {
                throw new AssertionError();
            }
        }
    }

    /**
     * @param context Application context.
     * @return Gets the log tag for the application.
     */
    @NonNull
    private static String getLogTag(@NonNull final Context context) {
        final String logTag = context.getString(R.string.com_twofortyfouram_log_tag);

        if (0 == logTag.length()) {
            return getApplicationName(context);
        }

        return logTag;
    }

    /**
     * Gets the name of the application, lowercased and spaces replaced with hyphens.
     *
     * @param context Application context.
     */
    @NonNull
    private static String getApplicationName(@NonNull final Context context) {
        final String name = AppBuildInfo.getApplicationName(context);

        /*
         * This is a developer-oriented string, so using Locale.US is appropriate. Replacing spaces
         * makes filtering for the log tag on the command line adb client easier.
         */
        return name.toLowerCase(Locale.US).replaceAll(" ", "-"); //$NON-NLS-1$//$NON-NLS-2$
    }

    /**
     * Private constructor prevents instantiation.
     *
     * @throws UnsupportedOperationException because this class cannot be instantiated.
     */
    private Lumberjack() {
        throw new UnsupportedOperationException("This class is non-instantiable"); //$NON-NLS-1$
    }
}
