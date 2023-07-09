package com.luckylau.wheel.common.uti;

import java.io.Serializable;

public class FileChangeEvent implements Serializable {

    private static final long serialVersionUID = -4255584033113954765L;

    private String paths;

    private Object context;

    public static FileChangeEventBuilder builder() {
        return new FileChangeEventBuilder();
    }

    public String getPaths() {
        return paths;
    }

    public void setPaths(String paths) {
        this.paths = paths;
    }

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }

    @Override
    public String toString() {
        return "FileChangeEvent{" + "paths='" + paths + '\'' + ", context=" + context + '}';
    }

    public static final class FileChangeEventBuilder {

        private String paths;

        private Object context;

        private FileChangeEventBuilder() {
        }

        public FileChangeEventBuilder paths(String paths) {
            this.paths = paths;
            return this;
        }

        public FileChangeEventBuilder context(Object context) {
            this.context = context;
            return this;
        }

        /**
         * build FileChangeEvent.
         *
         * @return {@link FileChangeEvent}
         */
        public FileChangeEvent build() {
            FileChangeEvent fileChangeEvent = new FileChangeEvent();
            fileChangeEvent.setPaths(paths);
            fileChangeEvent.setContext(context);
            return fileChangeEvent;
        }
    }
}
