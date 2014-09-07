package com.ui4j.api.dialog;

import com.ui4j.api.util.Logger;
import com.ui4j.api.util.LoggerFactory;

class AlertDialogHandler implements AlertHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AlertDialogHandler.class);

    @Override
    public void handle(DialogEvent event) {
        LOG.info("Alert message: " + event.getMessage());
    }
}
