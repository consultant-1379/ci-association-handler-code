/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2012
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 *----------------------------------------------------------------------------*/
package com.ericsson.oss.models.base.mediation.handlers;

import static com.ericsson.oss.mediation.engine.api.MediationEngineConstants.PTR_FDN;
import static com.ericsson.oss.mediation.engine.api.MediationEngineConstants.REMOTE_HOST_ATTR;
import static com.ericsson.oss.mediation.engine.api.MediationEngineConstants.REMOTE_PORT_ATTR;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.rmi.RemoteException;

import javax.naming.InitialContext;
import javax.rmi.PortableRemoteObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.ericsson.oss.itpf.common.config.Configuration;
import com.ericsson.oss.itpf.common.event.handler.EventHandlerContext;
import com.ericsson.oss.itpf.common.event.handler.exception.EventHandlerException;
import com.ericsson.oss.itpf.datalayer.dps.remote.RemoteDataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.remote.RemoteDataPersistenceServiceHome;
import com.ericsson.oss.itpf.datalayer.dps.remote.dto.ManagedObjectDto;
import com.ericsson.oss.itpf.datalayer.dps.remote.exception.DataPersistenceServiceRemoteException;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ PortableRemoteObject.class })
public class CiAssociationHandlerTest {

    private final static long EAI_PO_ID = 1111;
    private final static long MF_PO_ID = 2222;
    private final static long CI_PO_ID = 3333;
    private final static String CI_FDN = "MeC=test,Me=test,ENodeBFunction=1,ConnectivityInfo=1";
    private final static String MF_FDN = "MeC=test,Me=test,ENodeBFunction=1";
    private static final String ENDPOINT_NAME = "ciRef";

    @InjectMocks
    private CiAssociationHandler ciAssociation;
    @Mock
    private RemoteDataPersistenceService remoteDpsMock;
    @Mock
    private RemoteDataPersistenceServiceHome remoteDpsHomeMock;
    @Mock
    private InitialContext initialContextMock;
    @Mock
    private EventHandlerContext eventHandlerContextMock;
    @Mock
    private Configuration configurationMock;

    private ManagedObjectDto ciMo;

    private ManagedObjectDto mfMo;

    private static String LIVE_BUCKET = null;

    private static String EMPTY = "";

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(eventHandlerContextMock.getEventHandlerConfiguration()).thenReturn(configurationMock);
        when(configurationMock.getStringProperty(PTR_FDN)).thenReturn(CI_FDN);
        when(configurationMock.getStringProperty(REMOTE_HOST_ATTR)).thenReturn("127.0.0.1");
        when(configurationMock.getStringProperty(REMOTE_PORT_ATTR)).thenReturn("3528");
        setupGoodDps();
        ciMo = new ManagedObjectDto("namespace", "type", "version", CI_PO_ID, new Long(EAI_PO_ID), CI_FDN, "ciName");
        mfMo = new ManagedObjectDto("namespace", "type", "version", MF_PO_ID, new Long(EAI_PO_ID), MF_FDN, "mfName");
    }

    @Test(expected = EventHandlerException.class)
    public void testFdnWhenEmpty() throws Exception {
        ciAssociation.init(eventHandlerContextMock);
        when(configurationMock.getStringProperty(PTR_FDN)).thenReturn(EMPTY);
        ciAssociation.init(eventHandlerContextMock);
    }

    @Test(expected = EventHandlerException.class)
    public void testHostWhenEmpty() throws Exception {
        ciAssociation.init(eventHandlerContextMock);
        when(configurationMock.getStringProperty(REMOTE_HOST_ATTR)).thenReturn(EMPTY);
        ciAssociation.init(eventHandlerContextMock);
    }

    @Test(expected = EventHandlerException.class)
    public void testPortWhenEmpty() throws Exception {
        ciAssociation.init(eventHandlerContextMock);
        when(configurationMock.getStringProperty(REMOTE_PORT_ATTR)).thenReturn(EMPTY);
        ciAssociation.init(eventHandlerContextMock);
    }

    @Test
    public void testSuccessfulAssociation() throws Exception {
        ciAssociation.init(eventHandlerContextMock);
        when(remoteDpsMock.getMo(LIVE_BUCKET, CI_FDN)).thenReturn(ciMo);
        when(remoteDpsMock.getMo(LIVE_BUCKET, MF_FDN)).thenReturn(mfMo);
        ciAssociation.onEvent(null);
        verify(remoteDpsMock).getMo(LIVE_BUCKET, CI_FDN);
        verify(remoteDpsMock, Mockito.times(0)).getMo(LIVE_BUCKET, MF_FDN);
        verify(remoteDpsMock).addAssociation(null, EAI_PO_ID, CI_PO_ID, ENDPOINT_NAME);
    }

    @Test(expected = EventHandlerException.class)
    public void testCiDoesNotExist() throws Exception {
        ciAssociation.init(eventHandlerContextMock);
        when(remoteDpsMock.getMo(LIVE_BUCKET, CI_FDN)).thenReturn(null);
        ciAssociation.onEvent(null);
    }

    @Test(expected = EventHandlerException.class)
    public void testMfDoesNotExist() throws Exception {
        ciAssociation.init(eventHandlerContextMock);
        ciMo = new ManagedObjectDto("namespace", "type", "version", CI_PO_ID, null, CI_FDN, "ciName");
        when(remoteDpsMock.getMo(LIVE_BUCKET, CI_FDN)).thenReturn(ciMo);
        ciAssociation.onEvent(null);
        verify(remoteDpsMock).getMo(LIVE_BUCKET, CI_FDN);
        verify(remoteDpsMock, Mockito.times(0)).getMo(LIVE_BUCKET, MF_FDN);
    }

    @Test(expected = EventHandlerException.class)
    public void testGetCiMoThrowsException() throws Exception {
        ciAssociation.init(eventHandlerContextMock);
        Mockito.doThrow(new DataPersistenceServiceRemoteException("Unavailable")).when(remoteDpsMock).getMo(LIVE_BUCKET, CI_FDN);
        ciAssociation.onEvent(null);
        verify(remoteDpsMock, Mockito.times(0)).getMo(LIVE_BUCKET, MF_FDN);
    }

    @Test(expected = EventHandlerException.class)
    public void testAddAssociationThrowsException() throws Exception {
        ciAssociation.init(eventHandlerContextMock);
        when(remoteDpsMock.getMo(LIVE_BUCKET, CI_FDN)).thenReturn(ciMo);
        Mockito.doThrow(new DataPersistenceServiceRemoteException("")).when(remoteDpsMock).addAssociation(null, EAI_PO_ID, CI_PO_ID, ENDPOINT_NAME);
        ciAssociation.onEvent(null);
        verify(remoteDpsMock, Mockito.times(0)).getMo(LIVE_BUCKET, MF_FDN);

    }

    @Test(expected = EventHandlerException.class)
    public void testRemoteDpsLookupFails() throws Exception {
        ciAssociation.init(eventHandlerContextMock);
        when(remoteDpsMock.getMo(LIVE_BUCKET, CI_FDN)).thenReturn(ciMo);
        when(remoteDpsMock.getMo(LIVE_BUCKET, MF_FDN)).thenReturn(mfMo);
        setupBadDps();
        ciAssociation.onEvent(null);
    }

    private void setupGoodDps() throws Exception {
        PowerMockito.mockStatic(PortableRemoteObject.class);
        PowerMockito.when(PortableRemoteObject.narrow(any(Object.class), eq(RemoteDataPersistenceServiceHome.class))).thenReturn(remoteDpsHomeMock);
        when(initialContextMock.lookup(any(String.class))).thenReturn(new Object());
        when(remoteDpsHomeMock.create()).thenReturn(remoteDpsMock);
    }

    private void setupBadDps() throws Exception {
        PowerMockito.mockStatic(PortableRemoteObject.class);
        PowerMockito.when(PortableRemoteObject.narrow(any(Object.class), eq(RemoteDataPersistenceServiceHome.class))).thenReturn(remoteDpsHomeMock);
        when(initialContextMock.lookup(any(String.class))).thenReturn(new Object());
        when(remoteDpsHomeMock.create()).thenThrow(new RemoteException("Expected this!"));
    }

}
