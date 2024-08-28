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

import java.rmi.RemoteException;

import javax.ejb.CreateException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.PortableRemoteObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.itpf.common.config.Configuration;
import com.ericsson.oss.itpf.common.event.handler.EventHandlerContext;
import com.ericsson.oss.itpf.common.event.handler.EventInputHandler;
import com.ericsson.oss.itpf.common.event.handler.annotation.EventHandler;
import com.ericsson.oss.itpf.common.event.handler.exception.EventHandlerException;
import com.ericsson.oss.itpf.datalayer.dps.remote.RemoteDataPersistenceService;
import com.ericsson.oss.itpf.datalayer.dps.remote.RemoteDataPersistenceServiceHome;
import com.ericsson.oss.itpf.datalayer.dps.remote.dto.ManagedObjectDto;
import com.ericsson.oss.itpf.datalayer.dps.remote.exception.DataPersistenceServiceRemoteException;

/**
 * This handler is executed as part of a CM mediation add node boot strap synchronous flow. This means the DPS TX is still active and has not been
 * committed yet. Any runtime exception thrown here will roll back the DPS TX and the flow will fail. This handler does the following:<br>
 * <br>
 * 1. Does a look up of the remote DPS <br>
 * 2. Creates a new association from the <code>EntityAddressInfo</code> to the MO using an association end point name<br>
 *
 */
@EventHandler(contextName = "")
public class CiAssociationHandler implements EventInputHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CiAssociationHandler.class);
    private static final String CONFIG_ATTR_NULL_OR_EMPTY_MSG = "This Config attribute was Null or Empty: ";
    private static final String CONFIG_ATTR_MSG = "The config attributes are: {} ";
    private static final String GET_MO_OK_MSG = "Successfully retrieved PO id {} for MO {}";
    private static final String GET_MO_NOT_FOUND_MSG = "No MO exists with FDN: ";
    private static final String GET_MO_DPS_FAIL_MSG = "DPS exception while fetching MO from remote DPS: ";
    private static final String REMOTE_DPS_LOOKUP_FAIL_MSG = "Error doing jndi lookup of Remote Data Persistence service";
    private static final String REMOTE_DPS_LOOKUP_ATTEMPT_MSG = "Performing remote JNDI lookup with";
    private static final String REMOTE_DPS_LOOKUP_OK_MSG = "Remote DPS lookup successfull";
    private static final String ASSOC_CREATED_OK_MSG = "Successfully added assoc between EAI id {} and CI id {}: {}";
    private static final String ASSOC_CREATION_FAIL_MSG = "Unable to create association";
    private static final String ASSOC_CREATION_FAIL_MSG_DETAILED = ASSOC_CREATION_FAIL_MSG + " between EAI id {} and CI {}: {}";
    // TODO This should not be hard-coded to make it CDS compliant
    private static final String ENDPOINT_NAME = "ciRef";
    private static final String LIVE_BUCKET = null;
    private RemoteDataPersistenceService remoteDps;
    private InitialContext jndiContext;
    private String remoteHost;
    private String remotePort;
    private String ciFdn;

    @Override
    public void init(final EventHandlerContext ctx) {
        extractParameters(ctx.getEventHandlerConfiguration());
    }

    @Override
    public void onEvent(final Object inputEvent) {
        LOG.debug("onEvent called: " + this.getClass().getName());
        lookupRemoteDps();
        createAssociation();
        LOG.debug("onEvent finished: " + this.getClass().getName());
    }

    @Override
    public void destroy() {
    }

    /**
     * Does look up of the DPS using its remote interface (specifically designed for bootstrap use case as its all done within same TX and TX has not
     * been committed yet)
     */
    private void lookupRemoteDps() {
        try {
            if (jndiContext == null) {
                jndiContext = new InitialContext();
            }
            final String lookupString = "corbaname:iiop:" + remoteHost + ":" + remotePort + "#" + RemoteDataPersistenceServiceHome.REMOTE_LOOKUP_NAME;
            LOG.debug(REMOTE_DPS_LOOKUP_ATTEMPT_MSG + lookupString);
            final Object iiopObject = jndiContext.lookup(lookupString);
            final RemoteDataPersistenceServiceHome ejbHome = (RemoteDataPersistenceServiceHome) PortableRemoteObject.narrow(iiopObject,
                    RemoteDataPersistenceServiceHome.class);
            remoteDps = ejbHome.create();
        } catch (NamingException | RemoteException | CreateException e) {
            LOG.error(REMOTE_DPS_LOOKUP_FAIL_MSG, e);
            throw new EventHandlerException(REMOTE_DPS_LOOKUP_FAIL_MSG, e);
        }
        LOG.debug(REMOTE_DPS_LOOKUP_OK_MSG);
    }

    private void createAssociation() {
        final ManagedObjectDto ciMo = getCiMo();
        Long ciId = null;
        Long eaiId = null;
        try {
            ciId = ciMo.getPoId();
            eaiId = ciMo.getEntityAddressInfoId();
            remoteDps.addAssociation(LIVE_BUCKET, eaiId, ciId, ENDPOINT_NAME);
            LOG.debug(ASSOC_CREATED_OK_MSG, eaiId, ciId, ciFdn);
        } catch (RemoteException | DataPersistenceServiceRemoteException | NullPointerException e) {
            LOG.error(ASSOC_CREATION_FAIL_MSG_DETAILED, eaiId, ciId, ciFdn, e.getMessage());
            throw new EventHandlerException(ASSOC_CREATION_FAIL_MSG, e.getCause());
        }
    }

    private ManagedObjectDto getCiMo() {
        ManagedObjectDto ciMo = null;
        try {
            ciMo = remoteDps.getMo(LIVE_BUCKET, ciFdn);
            if (ciMo == null) {
                throw new EventHandlerException(GET_MO_NOT_FOUND_MSG + ciFdn);
            }
            LOG.debug(GET_MO_OK_MSG, ciMo.getPoId(), ciFdn);
        } catch (RemoteException | DataPersistenceServiceRemoteException e) {
            LOG.error(GET_MO_DPS_FAIL_MSG + ciFdn, e);
            throw new EventHandlerException(GET_MO_DPS_FAIL_MSG + ciFdn, e.getCause());
        }
        return ciMo;
    }

    private void extractParameters(final Configuration config) {
        LOG.info(CONFIG_ATTR_MSG, config.getAllProperties());
        remoteHost = config.getStringProperty(REMOTE_HOST_ATTR);
        verifyNotEmpty(remoteHost, REMOTE_HOST_ATTR);
        remotePort = config.getStringProperty(REMOTE_PORT_ATTR);
        verifyNotEmpty(remotePort, REMOTE_PORT_ATTR);
        ciFdn = config.getStringProperty(PTR_FDN);
        verifyNotEmpty(ciFdn, PTR_FDN);
    }

    /*
     * Throws exception if value is empty. No need to check if it's null as this is already done when Configuration.getStringProperty is called.
     */
    private void verifyNotEmpty(final String value, final String propName) {
        if (value.isEmpty()) {
            throw new EventHandlerException(CONFIG_ATTR_NULL_OR_EMPTY_MSG + propName);
        }
    }

}
