package com.luckylau.wheel.common.uti;

import java.nio.file.WatchEvent;
import java.util.concurrent.Executor;

public abstract class FileWatcher {

    /**
     * Triggered when a file change occurs.
     *
     * @param event {@link FileChangeEvent}
     */
    public abstract void onChange(FileChangeEvent event);

    /**
     * WatchEvent context information.
     *
     * @param context {@link WatchEvent#context()}
     * @return is this watcher interest context
     */
    public abstract boolean interest(String context);

    /**
     * If the FileWatcher has its own thread pool, use this thread pool to execute, otherwise use the WatchFileManager
     * thread.
     *
     * @return {@link Executor}
     */
    public Executor executor() {
        return null;
    }

}
