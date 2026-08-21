// Copyright (C) 2026 Red Hat, Inc. and individual contributors as indicated by the @author tags.
// You may not use this file except in compliance with the Apache License, Version 2.0.

package io.agroal.test.basic;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.test.MockConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.agroal.test.AgroalTestGroup.FUNCTIONAL;
import static io.agroal.test.MockDriver.deregisterMockDriver;
import static io.agroal.test.MockDriver.registerMockDriver;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the Request Boundaries API ({@link Connection#beginRequest()} / {@link Connection#endRequest()}),
 * required by features such as Oracle's Transparent Application Continuity (TAC).
 *
 * @author <a href="gegastaldi@gmail.com">George Gastaldi</a>
 */
@Tag( FUNCTIONAL )
public class RequestBoundariesTests {

    @BeforeAll
    static void setupMockDriver() {
        registerMockDriver( RequestBoundariesConnection.class );
    }

    @AfterAll
    static void teardown() {
        deregisterMockDriver();
    }

    @BeforeEach
    void resetCounters() {
        RequestBoundariesConnection.beginRequestCount.set( 0 );
        RequestBoundariesConnection.endRequestCount.set( 0 );
    }

    // --- //

    @Test
    @DisplayName( "beginRequest() and endRequest() are delegated to the underlying connection" )
    void requestBoundariesAreDelegated() throws SQLException {
        try ( AgroalDataSource dataSource = AgroalDataSource.from( new AgroalDataSourceConfigurationSupplier().connectionPoolConfiguration( cp -> cp.maxSize( 1 ) ) ) ) {
            try ( Connection connection = dataSource.getConnection() ) {
                connection.beginRequest();
                connection.endRequest();

                assertAll( () -> {
                    assertEquals( 1, RequestBoundariesConnection.beginRequestCount.get(), "beginRequest() was not delegated to the underlying connection" );
                    assertEquals( 1, RequestBoundariesConnection.endRequestCount.get(), "endRequest() was not delegated to the underlying connection" );
                } );
            }
        }
    }

    @Test
    @DisplayName( "beginRequest() and endRequest() throw on a closed connection" )
    void requestBoundariesThrowWhenClosed() throws SQLException {
        try ( AgroalDataSource dataSource = AgroalDataSource.from( new AgroalDataSourceConfigurationSupplier().connectionPoolConfiguration( cp -> cp.maxSize( 1 ) ) ) ) {
            Connection connection = dataSource.getConnection();
            connection.close();

            assertAll( () -> {
                assertThrows( SQLException.class, connection::beginRequest, "Expected SQLException on closed Connection" );
                assertThrows( SQLException.class, connection::endRequest, "Expected SQLException on closed Connection" );
                assertEquals( 0, RequestBoundariesConnection.beginRequestCount.get(), "beginRequest() should not reach a closed connection" );
                assertEquals( 0, RequestBoundariesConnection.endRequestCount.get(), "endRequest() should not reach a closed connection" );
            } );
        }
    }

    // --- //

    public static class RequestBoundariesConnection implements MockConnection {

        private static final AtomicInteger beginRequestCount = new AtomicInteger();
        private static final AtomicInteger endRequestCount = new AtomicInteger();

        @Override
        public void beginRequest() throws SQLException {
            beginRequestCount.incrementAndGet();
        }

        @Override
        public void endRequest() throws SQLException {
            endRequestCount.incrementAndGet();
        }
    }
}
