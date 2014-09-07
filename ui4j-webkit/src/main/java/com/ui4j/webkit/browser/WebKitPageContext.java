package com.ui4j.webkit.browser;

import java.util.List;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebErrorEvent;
import javafx.scene.web.WebView;

import com.ui4j.api.browser.PageConfiguration;
import com.ui4j.api.browser.SelectorEngine;
import com.ui4j.api.browser.SelectorType;
import com.ui4j.api.dom.Document;
import com.ui4j.api.dom.Element;
import com.ui4j.api.dom.Window;
import com.ui4j.api.event.DocumentListener;
import com.ui4j.api.event.DocumentLoadEvent;
import com.ui4j.api.util.Logger;
import com.ui4j.api.util.LoggerFactory;
import com.ui4j.spi.EventManager;
import com.ui4j.spi.EventRegistrar;
import com.ui4j.spi.JavaScriptEngine;
import com.ui4j.spi.NativeEventManager;
import com.ui4j.spi.PageContext;
import com.ui4j.webkit.proxy.WebKitProxy;
import com.ui4j.webkit.spi.SizzleSelectorEngine;
import com.ui4j.webkit.spi.W3CEventRegistrar;
import com.ui4j.webkit.spi.W3CSelectorEngine;
import com.ui4j.webkit.spi.WebKitJavaScriptEngine;

public class WebKitPageContext implements PageContext {

    private Logger log = LoggerFactory.getLogger(getClass());

    private EventRegistrar eventRegistrar;

    private EventManager eventManager = new NativeEventManager(this);

    private SelectorEngine selector;

    private PageConfiguration configuration;

    private WebKitProxy elementFactory;

    private WebKitProxy documentFactory;

    private WebKitProxy windowFactory;

    private WebKitProxy pageFactory;

    public static class ErrorEventHandlerImpl implements EventHandler<WebErrorEvent> {

        private Logger log = LoggerFactory.getLogger(getClass());

        @Override
        public void handle(WebErrorEvent event) {
            log.error("Javascript error: " + event.getMessage());
        }
    }

    public static class ExceptionListener implements ChangeListener<Throwable> {

        private Logger log;

        public ExceptionListener(Logger log) {
            this.log = log;
        }

        @Override
        public void changed(ObservableValue<? extends Throwable> observable,
                Throwable oldValue, Throwable newValue) {
            log.error(newValue.getMessage());
        }
    };

    public static class GlobalEventCleaner implements DocumentListener {

        @Override
        public void onLoad(DocumentLoadEvent event) {
            Document document = event.getDocument();
            List<Element> elements = document.queryAll("[ui4j-registered-event=true]");
            if (elements.isEmpty()) {
                return;
            }
            for (Element next : elements) {
                next.unbind();
                next.removeAttribute("ui4j-registered-event");
                next.removeData("events");
            }
        }
    }

    public WebKitPageContext(PageConfiguration configuration,
                        WebKitProxy elementFactory,
                        WebKitProxy documentFactory,
                        WebKitProxy windowFactory,
                        WebKitProxy pageFactory) {
        this.configuration = configuration;
        this.elementFactory = elementFactory;
        this.documentFactory = documentFactory;
        this.windowFactory = windowFactory;
        this.pageFactory = pageFactory;
    }

    @Override
    public EventManager getEventManager() {
        return eventManager;
    }

    @Override
    public EventRegistrar getEventRegistrar() {
        return eventRegistrar;
    }

    public Element createElement(Object node, Document document, JavaScriptEngine engine) {
        return (Element) elementFactory.newInstance(new Object[] { node, document, this, engine });
    }

    public Document createDocument(JavaScriptEngine engine) {
        WebEngine webEngine = (WebEngine) engine.getEngine();
        webEngine.getLoadWorker().exceptionProperty().addListener(new ExceptionListener(log));
        webEngine.setOnError(new ErrorEventHandlerImpl());
        if (configuration.getUserAgent() != null) {
            webEngine.setUserAgent(configuration.getUserAgent());
        }
        Document document = (Document) documentFactory.newInstance(new Object[] { this, engine });
        initializeSizzle(document, (WebKitJavaScriptEngine) engine);
        return document;
    }

    public void onLoad(Document document) {
        this.eventRegistrar = new W3CEventRegistrar(this);
    }

    public Window createWindow(Document document) {
        return (Window) windowFactory.newInstance(new Object[] { document });
    }

    public WebKitPage newPage(Object view, JavaScriptEngine engine, Window window, Document document) {
        WebView webView = (WebView) view;
        WebKitPage page = (WebKitPage) pageFactory.newInstance(new Object[] { webView, engine, window, document });
        page.addDocumentListener(new GlobalEventCleaner());
        return page;
    }

    @Override
    public SelectorEngine getSelectorEngine() {
        return selector;
    }

    protected void initializeSizzle(Document document, WebKitJavaScriptEngine engine) {
        if (configuration.getSelectorEngine().equals(SelectorType.SIZZLE)) {
            String sizzle = readSizzle();
            boolean foundSizzle = Boolean.parseBoolean(engine.getEngine().executeScript("typeof window.Sizzle === 'function'").toString());
            if (!foundSizzle) {
                engine.getEngine().executeScript(sizzle);
            }
            selector = new SizzleSelectorEngine(this, document, engine);
        } else {
            selector = new W3CSelectorEngine(this, document, engine);
        }
    }

    protected String readSizzle() {
        return readFromClasspath("/com/ui4j/webkit/sizzle.js");
    }

    protected String readFromClasspath(String path) {
        try(java.util.Scanner scanner = new java.util.Scanner(getClass().getResourceAsStream(path))) {
            scanner.useDelimiter("\\A");
            if (scanner.hasNext()) {
                return scanner.next();
            } else {
                return null;
            }
        }
    }

    public PageConfiguration getConfiguration() {
        return configuration;
    }
}
