package dev.kspog.pokelite.game;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class WindowsGameEmbedder {
    private static final Logger LOGGER = Logger.getLogger(WindowsGameEmbedder.class.getName());

    private static final int GWL_STYLE = -16;
    private static final int WS_CHILD = 0x40000000;
    private static final int WS_VISIBLE = 0x10000000;
    private static final int WS_CAPTION = 0x00C00000;
    private static final int WS_THICKFRAME = 0x00040000;
    private static final int WS_POPUP = 0x80000000;

    private final AtomicReference<WinDef.HWND> embeddedWindow = new AtomicReference<>();
    private volatile Canvas hostCanvas;
    private volatile boolean listenersInstalled;

    public boolean isSupported() {
        return System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");
    }

    public boolean embed(Process process, Canvas canvas, Duration timeout) throws InterruptedException {
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(canvas, "canvas");
        Objects.requireNonNull(timeout, "timeout");

        if (!isSupported()) {
            return false;
        }

        long deadline = System.nanoTime() + timeout.toNanos();
        WinDef.HWND gameWindow = null;

        while (System.nanoTime() < deadline && gameWindow == null) {
            gameWindow = findPokeMmoWindow(process.pid());
            if (gameWindow == null) {
                Thread.sleep(200L);
            }
        }

        if (gameWindow == null) {
            LOGGER.warning("Timed out while waiting for the PokeMMO window");
            return false;
        }

        if (!canvas.isDisplayable()) {
            LOGGER.warning("The PokeLite game host is not displayable");
            return false;
        }

        Pointer hostPointer = Native.getComponentPointer(canvas);
        if (hostPointer == null) {
            LOGGER.warning("Unable to obtain the native PokeLite host window");
            return false;
        }

        WinDef.HWND hostWindow = new WinDef.HWND(hostPointer);
        User32 user32 = User32.INSTANCE;
        user32.SetParent(gameWindow, hostWindow);

        int style = user32.GetWindowLong(gameWindow, GWL_STYLE);
        style &= ~(WS_CAPTION | WS_THICKFRAME | WS_POPUP);
        style |= WS_CHILD | WS_VISIBLE;
        user32.SetWindowLong(gameWindow, GWL_STYLE, style);

        embeddedWindow.set(gameWindow);
        hostCanvas = canvas;
        installHostListeners(canvas);
        resizeEmbeddedWindow();
        user32.ShowWindow(gameWindow, WinUser.SW_SHOW);
        user32.SetFocus(gameWindow);

        LOGGER.info("Embedded the PokeMMO window into PokeLite");
        return true;
    }

    public void resizeEmbeddedWindow() {
        WinDef.HWND gameWindow = embeddedWindow.get();
        Canvas canvas = hostCanvas;
        if (gameWindow == null || canvas == null || !canvas.isDisplayable()) {
            return;
        }

        Dimension size = canvas.getSize();
        if (size.width <= 0 || size.height <= 0) {
            return;
        }

        User32.INSTANCE.MoveWindow(gameWindow, 0, 0, size.width, size.height, true);
    }

    public void clear() {
        embeddedWindow.set(null);
        hostCanvas = null;
    }

    private WinDef.HWND findPokeMmoWindow(long expectedPid) {
        AtomicReference<WinDef.HWND> pidMatch = new AtomicReference<>();
        AtomicReference<WinDef.HWND> titleMatch = new AtomicReference<>();

        User32.INSTANCE.EnumWindows((window, data) -> {
            if (!User32.INSTANCE.IsWindowVisible(window)) {
                return true;
            }

            char[] titleBuffer = new char[512];
            User32.INSTANCE.GetWindowText(window, titleBuffer, titleBuffer.length);
            String title = Native.toString(titleBuffer).trim();
            if (!title.toLowerCase(Locale.ROOT).contains("pokemmo")) {
                return true;
            }

            titleMatch.compareAndSet(null, window);

            IntByReference processId = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(window, processId);
            if (Integer.toUnsignedLong(processId.getValue()) == expectedPid) {
                pidMatch.compareAndSet(null, window);
                return false;
            }
            return true;
        }, null);

        return pidMatch.get() != null ? pidMatch.get() : titleMatch.get();
    }

    private synchronized void installHostListeners(Canvas canvas) {
        if (listenersInstalled) {
            return;
        }

        canvas.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                resizeEmbeddedWindow();
            }
        });

        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                WinDef.HWND gameWindow = embeddedWindow.get();
                if (gameWindow != null) {
                    User32.INSTANCE.SetFocus(gameWindow);
                }
            }
        });

        listenersInstalled = true;
    }
}
