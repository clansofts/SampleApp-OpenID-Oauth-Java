package com.intuit.controller;

import com.intuit.utils.OpenIdHelper;
import org.apache.log4j.Logger;
import org.openid4java.association.AssociationSessionType;
import org.openid4java.consumer.ConsumerException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.InMemoryConsumerAssociationStore;
import org.openid4java.consumer.InMemoryNonceVerifier;
import org.openid4java.discovery.DiscoveryException;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.MessageException;
import org.openid4java.message.ax.FetchRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/*
 * This class is a controller for OpenID
 */

@Controller
public class OpenIdController {

    public static final Logger LOG = Logger.getLogger(OpenIdController.class);

    @RequestMapping(value="/initialize.htm", method= RequestMethod.GET)
    public void initialize(final HttpServletRequest request,
                           final HttpServletResponse response) throws IOException {
    LOG.info("Entered Initialize Function");

        LOG.info("### OpenIdController -> initialize() - started ###");

        String redirectTo;

        final HttpSession session = request.getSession();
        final OpenIdHelper openIDHelper = new OpenIdHelper(session);

        final List<DiscoveryInformation> discoveries = new ArrayList<DiscoveryInformation>();
        final ConsumerManager manager = new ConsumerManager();

        manager.setAssociations(new InMemoryConsumerAssociationStore());
        manager.setNonceVerifier(new InMemoryNonceVerifier(5000));
        manager.setMinAssocSessEnc(AssociationSessionType.DH_SHA256);

        DiscoveryInformation discovered = null;

        try {
            LOG.info("OpenID Provider URL = "
                    + openIDHelper.getProviderURL());
            discovered = new DiscoveryInformation(new URL(
                    openIDHelper.getProviderURL()));
        } catch (DiscoveryException e) {
            LOG.error(e.getLocalizedMessage());
        } catch (MalformedURLException me) {
            LOG.error(me.getLocalizedMessage());
        }

        discoveries.add(discovered);

        final DiscoveryInformation discoveryInfo = manager
                .associate(discoveries);
        request.getSession().setAttribute("openid-disc", discoveryInfo);

        final FetchRequest fetch = FetchRequest.createFetchRequest();

        try {
            fetch.addAttribute("FirstName",
                    "http://axschema.org/namePerson/first", true);
            fetch.addAttribute("LastName",
                    "http://axschema.org/namePerson/last", true);
            fetch.addAttribute("Email", "http://axschema.org/contact/email",
                    true);
            fetch.addAttribute("RealmId", "http://axschema.org/intuit/realmId",
                    true);
        } catch (MessageException e) {
            LOG.error(e.getLocalizedMessage());
        }

        fetch.setCount("Email", 3);

        AuthRequest authReq = null;
        LOG.info("openIdReturnUrl = " + openIDHelper.getReturnURL());
        try {
            authReq = manager.authenticate(discoveryInfo,
                    openIDHelper.getReturnURL());
            authReq.addExtension(fetch);
        } catch (MessageException e) {
            LOG.error(e.getLocalizedMessage());
        } catch (ConsumerException e) {
            LOG.error(e.getLocalizedMessage());
        }


        LOG.info("Session Id : " + session.getId());
        session.setAttribute("consumerManager", manager);
        LOG.info("authReq.getDestinationUrl: "
                + authReq.getDestinationUrl(true));
        LOG.info("### OpenIdController -> initialize() - completed ###");

        response.sendRedirect(authReq.getDestinationUrl(true));
    }

    /*
	 * This method is a call method used by the initialize method to verify the
	 * OpenId received by the Intuit OpenID Provider.
	 */
    @RequestMapping(value = "/verifyopenid.htm", method = RequestMethod.GET)
    public String verifyOpenIDFromIntuit(final HttpServletRequest request) {

        LOG.info("### OpenIdController -> verifyOpenIDFromIntuit() - started ###");

        String redirectTo;

        final HttpSession session = request.getSession();
        final OpenIdHelper openIDHelper = new OpenIdHelper(session);

        final Identifier identifier = openIDHelper.verifyResponse(request);
        LOG.info("OpenID identifier:"
                + ((identifier == null) ? "null" : identifier.getIdentifier()));

        final String identity = request.getParameter("openid.identity");
        LOG.info("openid.identity: " + identity);

        final String firstName = request.getParameter("openid.alias3.value.alias1");
        LOG.info("openid.alias3.value.alias1: " + firstName);

        final String lastName = request.getParameter("openid.alias3.value.alias2");
        LOG.info("openid.alias3.value.alias2: " + lastName);

        final String email = request.getParameter("openid.alias3.value.alias3");
        LOG.info("openid.alias3.value.alias3: " + email);

        final String realmId = request.getParameter("openid.alias3.value.alias4");
        LOG.info("openid.alias3.value.alias4: " + realmId);

        //Save Intuit User information in the Session
        session.setAttribute("openIDidentity", identity);
        session.setAttribute("firstName", firstName);
        session.setAttribute("lastName", lastName);
        session.setAttribute("email", email);
        session.setAttribute("openidstatus", "verified");
        session.setAttribute("connectionStatus", "not_authorized");
        session.setAttribute("signIntuit", true);

        LOG.info("### OpenIdController -> verifyOpenIDFromIntuit() - completed ###");

        //Continue Try-Buy Flow if user connected by direct connect
        if(request.getSession().getAttribute("direct_connect") != null &&
                request.getSession().getAttribute("direct_connect").equals("true")){
            redirectTo = "directconnect";
        }
        else{
            redirectTo = "redirect:/";
        }

        return redirectTo;
    }


}
