/*
 * $Url: $
 * $Id: $
 *
 * Copyright 1997-2005 Day Management AG
 * Barfuesserplatz 6, 4001 Basel, Switzerland
 * All Rights Reserved.
 *
 * This software is the confidential and proprietary information of
 * Day Management AG, ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Day.
 */
package org.apache.sling.core.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.sling.component.ComponentRequest;
import org.apache.sling.component.ComponentRequestWrapper;

/**
 * The <code>ComponentServletRequestWrapper</code> class is a
 * <code>ComponentRequestWrapper</code> which does not delegate to a wrapped
 * <code>ComponentRequest</code> but to a wrapped
 * <code>HttpServletRequest</code>. This is required if any user of the
 * <code>RequestDispatcher.include</code> method uses a
 * <code>HttpServletRequestWrapper</code> instead of a
 * <code>ComponentRequestWrapper</code>.
 */
public class ComponentServletRequestWrapper extends ComponentRequestWrapper {

    private final HttpServletRequest request;

    public ComponentServletRequestWrapper(ComponentRequest delegatee,
            HttpServletRequest request) {
        super(delegatee);
        this.request = request;
    }

    @Override
    public String getLocalAddr() {
        return request.getLocalAddr();
    }

    @Override
    public String getLocalName() {
        return request.getLocalName();
    }

    @Override
    public int getLocalPort() {
        return request.getLocalPort();
    }

    @Override
    public int getRemotePort() {
        return request.getRemotePort();
    }

    @Override
    public String getAuthType() {
        return request.getAuthType();
    }

    @Override
    public String getContextPath() {
        return request.getContextPath();
    }

    @Override
    public Cookie[] getCookies() {
        return request.getCookies();
    }

    @Override
    public long getDateHeader(String name) {
        return request.getDateHeader(name);
    }

    @Override
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    @Override
    public Enumeration getHeaderNames() {
        return request.getHeaderNames();
    }

    @Override
    public Enumeration getHeaders(String name) {
        return request.getHeaders(name);
    }

    @Override
    public int getIntHeader(String name) {
        return request.getIntHeader(name);
    }

    @Override
    public String getMethod() {
        return request.getMethod();
    }

    @Override
    public String getPathInfo() {
        return request.getPathInfo();
    }

    @Override
    public String getPathTranslated() {
        return request.getPathTranslated();
    }

    @Override
    public String getQueryString() {
        return request.getQueryString();
    }

    @Override
    public String getRemoteUser() {
        return request.getRemoteUser();
    }

    @Override
    public String getRequestURI() {
        return request.getRequestURI();
    }

    @Override
    public StringBuffer getRequestURL() {
        return request.getRequestURL();
    }

    @Override
    public String getRequestedSessionId() {
        return request.getRequestedSessionId();
    }

    @Override
    public String getServletPath() {
        return request.getServletPath();
    }

    @Override
    public HttpSession getSession() {
        return request.getSession();
    }

    @Override
    public HttpSession getSession(boolean create) {
        return request.getSession(create);
    }

    @Override
    public Principal getUserPrincipal() {
        return request.getUserPrincipal();
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return request.isRequestedSessionIdFromCookie();
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return request.isRequestedSessionIdFromURL();
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return request.isRequestedSessionIdFromUrl();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return request.isRequestedSessionIdValid();
    }

    @Override
    public boolean isUserInRole(String role) {
        return request.isUserInRole(role);
    }

    @Override
    public Object getAttribute(String name) {
        return request.getAttribute(name);
    }

    @Override
    public Enumeration getAttributeNames() {
        return request.getAttributeNames();
    }

    @Override
    public String getCharacterEncoding() {
        return request.getCharacterEncoding();
    }

    @Override
    public int getContentLength() {
        return request.getContentLength();
    }

    @Override
    public String getContentType() {
        return request.getContentType();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return request.getInputStream();
    }

    @Override
    public Locale getLocale() {
        return request.getLocale();
    }

    @Override
    public Enumeration getLocales() {
        return request.getLocales();
    }

    @Override
    public String getParameter(String name) {
        return request.getParameter(name);
    }

    @Override
    public Map getParameterMap() {
        return request.getParameterMap();
    }

    @Override
    public Enumeration getParameterNames() {
        return request.getParameterNames();
    }

    @Override
    public String[] getParameterValues(String name) {
        return request.getParameterValues(name);
    }

    @Override
    public String getProtocol() {
        return request.getProtocol();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return request.getReader();
    }

    @Override
    public String getRealPath(String path) {
        return request.getRealPath(path);
    }

    @Override
    public String getRemoteAddr() {
        return request.getRemoteAddr();
    }

    @Override
    public String getRemoteHost() {
        return request.getRemoteHost();
    }

    @Override
    public ServletRequest getRequest() {
        return request;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return request.getRequestDispatcher(path);
    }

    @Override
    public String getScheme() {
        return request.getScheme();
    }

    @Override
    public String getServerName() {
        return request.getServerName();
    }

    @Override
    public int getServerPort() {
        return request.getServerPort();
    }

    @Override
    public boolean isSecure() {
        return request.isSecure();
    }

    @Override
    public void removeAttribute(String name) {
        request.removeAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object o) {
        request.setAttribute(name, o);
    }

    @Override
    public void setCharacterEncoding(String enc)
            throws UnsupportedEncodingException {
        request.setCharacterEncoding(enc);
    }
}
