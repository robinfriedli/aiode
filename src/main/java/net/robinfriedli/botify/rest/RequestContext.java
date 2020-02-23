package net.robinfriedli.botify.rest;

import javax.servlet.http.HttpServletRequest;

public class RequestContext {

    private final HttpServletRequest request;

    public RequestContext(HttpServletRequest request) {
        this.request = request;
    }

    public HttpServletRequest getRequest() {
        return request;
    }
}
