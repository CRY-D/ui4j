package io.webfolder.ui4j.api.dialog;

@FunctionalInterface
public interface AlertHandler {

    void handle(DialogEvent event);
}
