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
package org.apache.camel.impl;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * This unit of work supports <a href="http://www.slf4j.org/api/org/slf4j/MDC.html">MDC</a>.
 *
 * @version 
 */
public class MDCUnitOfWork extends DefaultUnitOfWork {

    public static final String MDC_BREADCRUMB_ID = "breadcrumbId";
    public static final String MDC_EXCHANGE_ID = "exchangeId";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_ROUTE_ID = "routeId";
    public static final String MDC_CAMEL_CONTEXT_ID = "camelContextId";
    public static final String MDC_TRANSACTION_KEY = "transactionKey";

    private static final Logger LOG = LoggerFactory.getLogger(MDCUnitOfWork.class);

    private final String originalBreadcrumbId;
    private final String originalExchangeId;
    private final String originalCorrelationId;
    private final String originalRouteId;
    private final String originalCamelContextId;
    private final String originalTransactionKey;

    public MDCUnitOfWork(Exchange exchange) {
        super(exchange, LOG);

        // remember existing values
        this.originalExchangeId = MDC.get(MDC_EXCHANGE_ID);
        this.originalBreadcrumbId = MDC.get(MDC_BREADCRUMB_ID);
        this.originalCorrelationId = MDC.get(MDC_CORRELATION_ID);
        this.originalRouteId = MDC.get(MDC_ROUTE_ID);
        this.originalCamelContextId = MDC.get(MDC_CAMEL_CONTEXT_ID);
        this.originalTransactionKey = MDC.get(MDC_TRANSACTION_KEY);

        // must add exchange id in constructor
        MDC.put(MDC_EXCHANGE_ID, exchange.getExchangeId());
        // the camel context id is from exchange
        MDC.put(MDC_CAMEL_CONTEXT_ID, exchange.getContext().getName());
        // and add optional correlation id
        String corrId = exchange.getProperty(Exchange.CORRELATION_ID, String.class);
        if (corrId != null) {
            MDC.put(MDC_CORRELATION_ID, corrId);
        }
        // and add optional breadcrumb id
        String breadcrumbId = exchange.getIn().getHeader(Exchange.BREADCRUMB_ID, String.class);
        if (breadcrumbId != null) {
            MDC.put(MDC_BREADCRUMB_ID, breadcrumbId);
        }
    }

    @Override
    public UnitOfWork newInstance(Exchange exchange) {
        return new MDCUnitOfWork(exchange);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        // and remove when stopping
        clear();
    }

    @Override
    public void pushRouteContext(RouteContext routeContext) {
        MDC.put(MDC_ROUTE_ID, routeContext.getRoute().getId());
        super.pushRouteContext(routeContext);
    }

    @Override
    public RouteContext popRouteContext() {
        MDC.remove(MDC_ROUTE_ID);
        return super.popRouteContext();
    }

    @Override
    public void beginTransactedBy(Object key) {
        MDC.put(MDC_TRANSACTION_KEY, key.toString());
        super.beginTransactedBy(key);
    }

    @Override
    public void endTransactedBy(Object key) {
        MDC.remove(MDC_TRANSACTION_KEY);
        super.endTransactedBy(key);
    }

    @Override
    public AsyncCallback beforeProcess(Processor processor, Exchange exchange, AsyncCallback callback) {
        return new MDCCallback(callback);
    }

    @Override
    public void afterProcess(Processor processor, Exchange exchange, AsyncCallback callback, boolean doneSync) {
        if (!doneSync) {
            // must clear MDC on current thread as the exchange is being processed asynchronously
            // by another thread
            clear();
        }
        super.afterProcess(processor, exchange, callback, doneSync);
    }

    /**
     * Clears information put on the MDC by this {@link MDCUnitOfWork}
     */
    public void clear() {
        if (this.originalBreadcrumbId != null) {
            MDC.put(MDC_BREADCRUMB_ID, originalBreadcrumbId);
        } else {
            MDC.remove(MDC_BREADCRUMB_ID);
        }
        if (this.originalExchangeId != null) {
            MDC.put(MDC_EXCHANGE_ID, originalExchangeId);
        } else {
            MDC.remove(MDC_EXCHANGE_ID);
        }
        if (this.originalCorrelationId != null) {
            MDC.put(MDC_CORRELATION_ID, originalCorrelationId);
        } else {
            MDC.remove(MDC_CORRELATION_ID);
        }
        if (this.originalRouteId != null) {
            MDC.put(MDC_ROUTE_ID, originalRouteId);
        } else {
            MDC.remove(MDC_ROUTE_ID);
        }
        if (this.originalCamelContextId != null) {
            MDC.put(MDC_CAMEL_CONTEXT_ID, originalCamelContextId);
        } else {
            MDC.remove(MDC_CAMEL_CONTEXT_ID);
        }
        if (this.originalTransactionKey != null) {
            MDC.put(MDC_TRANSACTION_KEY, originalTransactionKey);
        } else {
            MDC.remove(MDC_TRANSACTION_KEY);
        }
    }

    @Override
    public String toString() {
        return "MDCUnitOfWork";
    }

    /**
     * {@link AsyncCallback} which preserves {@link org.slf4j.MDC} when
     * the asynchronous routing engine is being used.
     */
    private static final class MDCCallback implements AsyncCallback {

        private final AsyncCallback delegate;
        private final String breadcrumbId;
        private final String exchangeId;
        private final String correlationId;
        private final String routeId;
        private final String camelContextId;

        private MDCCallback(AsyncCallback delegate) {
            this.delegate = delegate;
            this.exchangeId = MDC.get(MDC_EXCHANGE_ID);
            this.breadcrumbId = MDC.get(MDC_BREADCRUMB_ID);
            this.correlationId = MDC.get(MDC_CORRELATION_ID);
            this.camelContextId = MDC.get(MDC_CAMEL_CONTEXT_ID);

            String routeId = MDC.get(MDC_ROUTE_ID);
            if (routeId != null) {
                // intern route id as this reduces memory allocations
                this.routeId = routeId.intern();
            } else {
                this.routeId = null;
            }
        }

        public void done(boolean doneSync) {
            try {
                if (!doneSync) {
                    // when done asynchronously then restore information from previous thread
                    if (breadcrumbId != null) {
                        MDC.put(MDC_BREADCRUMB_ID, breadcrumbId);
                    }
                    if (exchangeId != null) {
                        MDC.put(MDC_EXCHANGE_ID, exchangeId);
                    }
                    if (correlationId != null) {
                        MDC.put(MDC_CORRELATION_ID, correlationId);
                    }
                    if (routeId != null) {
                        MDC.put(MDC_ROUTE_ID, routeId);
                    }
                    if (camelContextId != null) {
                        MDC.put(MDC_CAMEL_CONTEXT_ID, camelContextId);
                    }
                }
            } finally {
                // muse ensure delegate is invoked
                delegate.done(doneSync);
            }
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

}
