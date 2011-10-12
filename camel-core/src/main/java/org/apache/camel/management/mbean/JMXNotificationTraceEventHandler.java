/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.management.mbean;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.management.Notification;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.Traceable;
import org.apache.camel.processor.interceptor.DefaultTraceEventHandler;
import org.apache.camel.processor.interceptor.TraceEventHandler;
import org.apache.camel.processor.interceptor.TraceInterceptor;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.util.MessageHelper;
import org.springframework.jmx.export.notification.NotificationPublisher;
import org.springframework.jmx.export.notification.NotificationPublisherAware;

public final class JMXNotificationTraceEventHandler implements TraceEventHandler, NotificationPublisherAware {
    private static final int MAX_MESSAGE_LENGTH = 60;
    private long num;
    private NotificationPublisher notificationSender;
    private Tracer tracer;
    private DefaultTraceEventHandler defaultTracer;

    public JMXNotificationTraceEventHandler(Tracer tracer) {
        this.tracer = tracer;
        this.defaultTracer = new DefaultTraceEventHandler(tracer);
    }

    @SuppressWarnings("rawtypes")
    public void traceExchangeOut(ProcessorDefinition node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange, Object traceState) throws Exception {
        defaultTracer.traceExchangeOut(node, target, traceInterceptor, exchange, traceState);
    }

    @SuppressWarnings("rawtypes")
    public Object traceExchangeIn(ProcessorDefinition node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
        return defaultTracer.traceExchangeIn(node, target, traceInterceptor, exchange);
    }

    @SuppressWarnings("rawtypes")
    public void traceExchange(ProcessorDefinition node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
        defaultTracer.traceExchange(node, target, traceInterceptor, exchange);
        if (notificationSender != null && tracer.isJmxTraceNotifications()) {
            String body = MessageHelper.extractBodyForLogging(exchange.getIn(), "", false, tracer.getTraceBodySize());
            
            if (body == null) {
                body = "";
            }
            String message = body.substring(0, Math.min(body.length(), MAX_MESSAGE_LENGTH));
            Map tm = createTraceMessage(node, exchange, body);

            Notification notification = new Notification("TraceNotification", exchange.toString(), num++, System.currentTimeMillis(), message);
            notification.setUserData(tm);

            notificationSender.sendNotification(notification);
        }

    }

    private Map<String, Object> createTraceMessage(ProcessorDefinition<?> node, Exchange exchange, String body) {
        Map<String, Object> mi = new HashMap<String, Object>();
        mi.put("ExchangeId", exchange.getExchangeId());
        mi.put("EndpointURI", getEndpointUri(node));
        mi.put("TimeStamp", new Date(System.currentTimeMillis()));
        mi.put("Body", body);

        Message message = exchange.getIn();
        Map<String, Object> sHeaders = message.getHeaders();
        Map<String, Object> sProperties = exchange.getProperties();

        Map<String, String> headers = new HashMap<String, String>();
        for (String key : sHeaders.keySet()) {
            headers.put(key, message.getHeader(key, String.class));
        }
        mi.put("Headers", headers);

        Map<String, String> properties = new HashMap<String, String>();
        for (String key : sProperties.keySet()) {
            properties.put(key, exchange.getProperty(key, String.class));
        }
        mi.put("Properties", properties);
        return mi;
    }

    private String getEndpointUri(ProcessorDefinition<?> node) {
        if (node instanceof Traceable) {
            Traceable tr = (Traceable)node;
            return tr.getTraceLabel();
        } else {
            return node.getLabel();
        }
    }

    @Override
    public void setNotificationPublisher(NotificationPublisher notificationPublisher) {
        this.notificationSender = notificationPublisher;
    }

}