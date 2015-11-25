package com.phoenix.service.revocation;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.phoenix.service.PhoenixDataService;
import com.phoenix.service.PhoenixServerCASigner;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.ocsp.RevokedInfo;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Servlet implementing server side of the Online Certificate Status Protocol (OCSP)
 * For a detailed description of OCSP refer to RFC2560.
 *
 * @web.servlet name = "OCSP"
 *              display-name = "OCSPServlet"
 *              description="Answers OCSP requests"
 *              load-on-startup = "99"
 *
 * @web.servlet-mapping url-pattern = "/ocsp"
 *
 * @web.servlet-init-param description="Algorithm used by server to generate signature on OCSP responses"
 *   name="SignatureAlgorithm"
 *   value="SHA1WithRSA"
 *
 * @web.servlet-init-param description="If set to true the servlet will enforce OCSP request signing"
 *   name="enforceRequestSigning"
 *   value="false"
 *
 * @web.servlet-init-param description="If set to true the certificate chain will be returned with the OCSP response"
 *   name="includeCertChain"
 *   value="true"
 *
 * @web.servlet-init-param description="If set to true the OCSP reponses will be signed directly by the CAs certificate instead of the CAs OCSP responder"
 *   name="useCASigningCert"
 *   value="${ocsp.usecasigningcert}"
 *
 * @web.servlet-init-param description="Specifies the subject of a certificate which is used to identifiy the responder which will generate responses when no real CA can be found from the request. This is used to generate 'unknown' responses when a request is received for a certificate that is not signed by any CA on this server"
 *   name="defaultResponderID"
 *   value="${ocsp.defaultresponder}"
 *
 *
 * @web.ejb-local-ref
 *  name="ejb/CertificateStoreSessionLocal"
 *  type="Session"
 *  link="CertificateStoreSession"
 *  home="se.anatom.ejbca.ca.store.ICertificateStoreSessionLocalHome"
 *  local="se.anatom.ejbca.ca.store.ICertificateStoreSessionLocal"
 *
 * @web.ejb-local-ref
 *  name="ejb/RSASignSessionLocal"
 *  type="Session"
 *  link="RSASignSession"
 *  home="se.anatom.ejbca.ca.sign.ISignSessionLocalHome"
 *  local="se.anatom.ejbca.ca.sign.ISignSessionLocal"
 *
 * @web.ejb-local-ref
 *  name="ejb/CAAdminSessionLocal"
 *  type="Session"
 *  link="CAAdminSession"
 *  home="se.anatom.ejbca.ca.caadmin.ICAAdminSessionLocalHome"
 *  local="se.anatom.ejbca.ca.caadmin.ICAAdminSessionLocal"
 *
 * @author Thomas Meckel (Ophios GmbH), Tomas Gustavsson
 * @version  $Id: OCSPServlet.java,v 1.40 2005-05-27 14:50:53 anatom Exp $
 */
@Service
@Repository
@Controller
public class RevocationOCSP {
    private static final Logger log = Logger.getLogger(RevocationOCSP.class);

    @Autowired
    private PhoenixDataService dataService;

    @Autowired(required = true)
    private PhoenixServerCASigner signer;

    @Autowired(required = true)
    private RevocationManager manager;

    @Autowired(required = true)
    private RevocationExecutor executor;

    private String m_sigAlg;
    private boolean m_reqMustBeSigned;
    private Collection m_cacerts = null;
    /** Cache time counter */
    private long m_certValidTo = 0;
    /** Cached list of cacerts is valid 5 minutes */
    private static final long VALID_TIME = 5 * 60 * 1000;
    /** String used to identify default responder id, used to generatwe responses when a request
     * for a certificate not signed by a CA on this server is received.
     */
    private String m_defaultResponderId;
    /** Marks if the CAs certificate or the CAs OCSP responder certificate should be used for
     * signing the OCSP response. Defined in web.xml
     */
    private boolean m_useCASigningCert;
    /** Marks if the CAs certificate chain shoudl be included in the OCSP response or not
     * Defined in web.xml
     */
    private boolean m_includeChain;

    @PostConstruct
    public synchronized void init() {
        log.info("Initializing RevocationOCSP");
    }

    @PreDestroy
    public synchronized void deinit(){
        log.info("Shutting down RevocationOCSP");
    }

//    /** Loads cacertificates but holds a cache so it's reloaded only every five minutes is needed.
//     */
//    protected synchronized void loadCertificates() throws IOException {
//        // Kolla om vi har en cachad collection och om den inte ?r f?r gammal
//        if (m_cacerts != null && m_certValidTo > new Date().getTime()) {
//            return;
//        }
//        try {
//            m_cacerts = m_certStore.findCertificatesByType(m_adm, CertificateDataBean.CERTTYPE_SUBCA + CertificateDataBean.CERTTYPE_ROOTCA, null);
//            m_certValidTo = new Date().getTime() + VALID_TIME;
//        } catch (Exception e) {
//            log.error("Unable to load CA certificates from CA store.", e);
//            throw new IOException(e.toString());
//        }
//    }
//
//    protected X509Certificate findCAByHash(CertificateID certId, Collection certs) throws OCSPException {
//        if (null == certId) {
//            throw new IllegalArgumentException();
//        }
//        if (null == certs || certs.isEmpty()) {
//            log.info("The passed certificate collection is empty.");
//            return null;
//        }
//        Iterator iter = certs.iterator();
//        while (iter.hasNext()) {
//            X509Certificate cacert = (X509Certificate) iter.next();
//            CertificateID issuerId = new CertificateID(certId.getHashAlgOID(), cacert, cacert.getSerialNumber());
//            if (log.isDebugEnabled()) {
//                log.debug("Comparing the following certificate hashes:\n"
//                        + " Hash algorithm : '" + certId.getHashAlgOID() + "'\n"
//                        + " CA certificate hashes\n"
//                        + "      Name hash : '" + Hex.encode(issuerId.getIssuerNameHash()) + "'\n"
//                        + "      Key hash  : '" + Hex.encode(issuerId.getIssuerKeyHash()) + "'\n"
//                        + " OCSP certificate hashes\n"
//                        + "      Name hash : '" + Hex.encode(certId.getIssuerNameHash()) + "'\n"
//                        + "      Key hash  : '" + Hex.encode(certId.getIssuerKeyHash()) + "'\n");
//            }
//            if ((issuerId.toASN1Object().getIssuerNameHash().equals(certId.toASN1Object().getIssuerNameHash()))
//                    && (issuerId.toASN1Object().getIssuerKeyHash().equals(certId.toASN1Object().getIssuerKeyHash()))) {
//                log.debug("Found matching CA-cert with:\n"
//                        + "      Name hash : '" + Hex.encode(issuerId.getIssuerNameHash()) + "'\n"
//                        + "      Key hash  : '" + Hex.encode(issuerId.getIssuerKeyHash()) + "'\n");
//                return cacert;
//            }
//        }
//        log.debug("Did not find matching CA-cert for:\n"
//                + "      Name hash : '" + Hex.encode(certId.getIssuerNameHash()) + "'\n"
//                + "      Key hash  : '" + Hex.encode(certId.getIssuerKeyHash()) + "'\n");
//        return null;
//    }
//
//    protected X509Certificate findCertificateBySubject(String subjectDN, Collection certs) {
//        if (certs == null || null == subjectDN) {
//            throw new IllegalArgumentException();
//        }
//
//        if (null == certs || certs.isEmpty()) {
//            log.info("The passed certificate collection is empty.");
//            return null;
//        }
//        String dn = CertTools.stringToBCDNString(subjectDN);
//        Iterator iter = certs.iterator();
//        while (iter.hasNext()) {
//            X509Certificate cacert = (X509Certificate) iter.next();
//            if (log.isDebugEnabled()) {
//                log.debug("Comparing the following certificates:\n"
//                        + " CA certificate DN: " + cacert.getSubjectDN()
//                        + "\n Subject DN: " + dn);
//            }
//            if (dn.equalsIgnoreCase(CertTools.stringToBCDNString(cacert.getSubjectDN().getName()))) {
//                return cacert;
//            }
//        }
//        log.info("Did not find matching CA-cert for DN: " + subjectDN);
//        return null;
//    }
//
//    protected BasicOCSPRespGenerator createOCSPResponse(OCSPReq req, X509Certificate cacert) throws OCSPException, NotSupportedException {
//        if (null == req) {
//            throw new IllegalArgumentException();
//        }
//        BasicOCSPRespGenerator res = new BasicOCSPRespGenerator(cacert.getPublicKey());
//        DERObjectIdentifier id_pkix_ocsp_nonce = new DERObjectIdentifier(OCSPObjectIdentifiers.pkix_ocsp + ".2");
//        DERObjectIdentifier id_pkix_ocsp_response = new DERObjectIdentifier(OCSPObjectIdentifiers.pkix_ocsp + ".4");
//        DERObjectIdentifier id_pkix_ocsp_basic = new DERObjectIdentifier(OCSPObjectIdentifiers.pkix_ocsp + ".1");
//        X509Extensions reqexts = req.getRequestExtensions();
//        if (reqexts != null) {
//            X509Extension ext = reqexts.getExtension(id_pkix_ocsp_nonce);
//            if (null != ext) {
//                //log.debug("Found extension Nonce");
//                Hashtable table = new Hashtable();
//                table.put(id_pkix_ocsp_nonce, ext);
//                X509Extensions exts = new X509Extensions(table);
//                res.setResponseExtensions(exts);
//            }
//            ext = reqexts.getExtension(id_pkix_ocsp_response);
//            if (null != ext) {
//                //log.debug("Found extension AcceptableResponses");
//                ASN1OctetString oct = ext.getValue();
//                try {
//                    ASN1Sequence seq = ASN1Sequence.getInstance(new ASN1InputStream(new ByteArrayInputStream(oct.getOctets())).readObject());
//                    Enumeration en = seq.getObjects();
//                    boolean supportsResponseType = false;
//                    while (en.hasMoreElements()) {
//                        DERObjectIdentifier oid = (DERObjectIdentifier) en.nextElement();
//                        //log.debug("Found oid: "+oid.getId());
//                        if (oid.equals(id_pkix_ocsp_basic)) {
//                            // This is the response type we support, so we are happy! Break the loop.
//                            supportsResponseType = true;
//                            log.debug("Response type supported: " + oid.getId());
//                            continue;
//                        }
//                    }
//                    if (!supportsResponseType) {
//                        throw new NotSupportedException("Required response type not supported, this responder only supports id-pkix-ocsp-basic.");
//                    }
//                } catch (IOException e) {
//                }
//            }
//        }
//        return res;
//    }
//
//    protected BasicOCSPResp signOCSPResponse(BasicOCSPRespGenerator basicRes, X509Certificate cacert)
//            throws CADoesntExistsException, ExtendedCAServiceRequestException, ExtendedCAServiceNotActiveException, IllegalExtendedCAServiceRequestException {
//        // Find the OCSP signing key and cert for the issuer
//        String issuerdn = CertTools.stringToBCDNString(cacert.getSubjectDN().toString());
//        int caid = issuerdn.hashCode();
//        BasicOCSPResp retval = null;
//        {
//            // Call extended CA services to get our OCSP stuff
//            OCSPCAServiceResponse caserviceresp = (OCSPCAServiceResponse) m_signsession.extendedService(m_adm, caid, new OCSPCAServiceRequest(basicRes, m_sigAlg, m_useCASigningCert, m_includeChain));
//            // Now we can use the returned OCSPServiceResponse to get private key and cetificate chain to sign the ocsp response
//            Collection coll = caserviceresp.getOCSPSigningCertificateChain();
//            log.debug("Cert chain for OCSP signing is of size " + coll.size());
//            retval = caserviceresp.getBasicOCSPResp();
//        }
//        return retval;
//    }
//
//    public void init(ServletConfig config)
//            throws ServletException {
//        super.init(config);
//
//        try {
//            ServiceLocator locator = ServiceLocator.getInstance();
//            ICertificateStoreSessionLocalHome castorehome =
//                    (ICertificateStoreSessionLocalHome) locator.getLocalHome(ICertificateStoreSessionLocalHome.COMP_NAME);
//            m_certStore = castorehome.create();
//            m_adm = new Admin(Admin.TYPE_INTERNALUSER);
//            ISignSessionLocalHome signhome = (ISignSessionLocalHome) locator.getLocalHome(ISignSessionLocalHome.COMP_NAME);
//            m_signsession = signhome.create();
//
//            // Parameters for OCSP signing (private) key
//            m_sigAlg = config.getInitParameter("SignatureAlgorithm");
//            if (StringUtils.isEmpty(m_sigAlg)) {
//                log.error("Signature algorithm not defined in initialization parameters.");
//                throw new ServletException("Missing signature algorithm in initialization parameters.");
//            }
//            m_defaultResponderId = config.getInitParameter("defaultResponderID");
//            if (StringUtils.isEmpty(m_defaultResponderId)) {
//                log.error("Default responder id not defined in initialization parameters.");
//                throw new ServletException("Missing default responder id in initialization parameters.");
//            }
//            String initparam = config.getInitParameter("enforceRequestSigning");
//            if (log.isDebugEnabled()) {
//                log.debug("Enforce request signing : '"
//                        + (StringUtils.isEmpty(initparam) ? "<not set>" : initparam)
//                        + "'");
//            }
//            m_reqMustBeSigned = true;
//            if (!StringUtils.isEmpty(initparam)) {
//                if (initparam.equalsIgnoreCase("false")
//                        || initparam.equalsIgnoreCase("no")) {
//                    m_reqMustBeSigned = false;
//                }
//            }
//            initparam = config.getInitParameter("useCASigningCert");
//            if (log.isDebugEnabled()) {
//                log.debug("Use CA signing cert : '"
//                        + (StringUtils.isEmpty(initparam) ? "<not set>" : initparam)
//                        + "'");
//            }
//            m_useCASigningCert = false;
//            if (!StringUtils.isEmpty(initparam)) {
//                if (initparam.equalsIgnoreCase("true")
//                        || initparam.equalsIgnoreCase("yes")) {
//                    m_useCASigningCert = true;
//                }
//            }
//            initparam = config.getInitParameter("includeCertChain");
//            if (log.isDebugEnabled()) {
//                log.debug("Include certificate chain: '"
//                        + (StringUtils.isEmpty(initparam) ? "<not set>" : initparam)
//                        + "'");
//            }
//            m_includeChain = true;
//            if (!StringUtils.isEmpty(initparam)) {
//                if (initparam.equalsIgnoreCase("false")
//                        || initparam.equalsIgnoreCase("no")) {
//                    m_includeChain = false;
//                }
//            }
//
//        } catch (Exception e) {
//            log.error("Unable to initialize OCSPServlet.", e);
//            throw new ServletException(e);
//        }
//    }

    @RequestMapping(value="/ca/ocsp", method=RequestMethod.POST)
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        log.debug(">doPost()");
        String contentType = request.getHeader("Content-Type");
        if (!contentType.equalsIgnoreCase("application/ocsp-request")) {
            log.debug("Content type is not application/ocsp-request");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Content type is not application/ocsp-request");
            return;
        }
        // Get the request data
        BufferedReader in = request.getReader();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // This works for small requests, and OCSP requests are small
        int b = in.read();
        while (b != -1) {
            baos.write(b);
            b = in.read();
        }
        baos.flush();
        in.close();
        byte[] reqBytes = baos.toByteArray();
        // Do it...
        //service(request, response, reqBytes);
        log.debug("<doPost()");
    } //doPost

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        log.debug(">doGet()");
        /**
         * We only support POST operation, so return
         * an appropriate HTTP error code to caller.
         */
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "OCSP only supports POST");
        log.debug("<doGet()");
    } // doGet

//    public void service(HttpServletRequest request, HttpServletResponse response, byte[] reqBytes) throws IOException, ServletException {
//        log.debug(">service()");
//        if ((reqBytes == null) || (reqBytes.length == 0)) {
//            log.debug("No request bytes");
//            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No request bytes.");
//            return;
//        }
//        try {
//            OCSPResp ocspresp = null;
//            BasicOCSPRespGenerator basicRes = null;
//            OCSPRespGenerator res = new OCSPRespGenerator();
//            X509Certificate cacert = null; // CA-certificate used to sign response
//            try {
//                OCSPReq req = new OCSPReq(reqBytes);
//                //log.debug("OCSPReq: "+new String(Base64.encode(req.getEncoded())));
//
//                loadCertificates();
//
//                if (log.isDebugEnabled()) {
//                    StringBuffer certInfo = new StringBuffer();
//                    Iterator iter = m_cacerts.iterator();
//                    while (iter.hasNext()) {
//                        X509Certificate cert = (X509Certificate) iter.next();
//                        certInfo.append(cert.getSubjectDN().getName());
//                        certInfo.append(',');
//                        certInfo.append(cert.getSerialNumber().toString());
//                        certInfo.append('\n');
//                    }
//                    log.debug("Found the following CA certificates : \n"
//                            + certInfo.toString());
//                }
//
//
//                /**
//                 * check the signature if contained in request.
//                 * if the request does not contain a signature
//                 * and the servlet is configured in the way
//                 * the a signature is required we send back
//                 * 'sigRequired' response.
//                 */
//                if (log.isDebugEnabled()) {
//                    log.debug("Incoming OCSP request is signed : " + req.isSigned());
//                }
//                if (m_reqMustBeSigned) {
//                    if (!req.isSigned()) {
//                        log.info("OCSP request unsigned. Servlet enforces signing.");
//                        throw new SignRequestException("OCSP request unsigned. Servlet enforces signing.");
//                    }
//                    //GeneralName requestor = req.getRequestorName();
//                    X509Certificate[] certs = req.getCerts("BC");
//                    // We must find a cert to verify the signature with...
//                    boolean verifyOK = false;
//                    for (int i = 0; i < certs.length; i++) {
//                        if (req.verify(certs[i].getPublicKey(), "BC") == true) {
//                            verifyOK = true;
//                            break;
//                        }
//                    }
//                    if (!verifyOK) {
//                        log.info("Signature of incoming OCSPRequest is invalid.");
//                        throw new SignRequestSignatureException("Signature invalid.");
//                    }
//                }
//
//                Req[] requests = req.getRequestList();
//                if (requests.length <= 0) {
//                    String msg = "The OCSP request does not contain any simpleRequest entities.";
//                    log.error(msg);
//                    {
//                        // All this just so we can create an error response
//                        cacert = findCertificateBySubject(m_defaultResponderId, m_cacerts);
//                        // Create a basicRes, just to create an error response
//                        basicRes = createOCSPResponse(req, cacert);
//                    }
//                    throw new MalformedRequestException(msg);
//                }
//                log.debug("The OCSP request contains " + requests.length + " simpleRequests.");
//                for (int i = 0; i < requests.length; i++) {
//                    CertificateID certId = requests[i].getCertID();
//                    boolean unknownCA = false; // if the certId was issued by an unknown CA
//                    // The algorithm here:
//                    // We will sign the response with the CA that issued the first
//                    // certificate(certId) in the request. If the issuing CA is not available
//                    // on this server, we sign the response with the default responderId (from params in web.xml).
//                    // We have to look up the ca-certificate for each certId in the request though, as we will check
//                    // for revocation on the ca-cert as well when checking for revocation on the certId.
//                    try {
//                        cacert = findCAByHash(certId, m_cacerts);
//                        if (cacert == null) {
//                            // We could not find certificate for this request so get certificate for default responder
//                            cacert = findCertificateBySubject(m_defaultResponderId, m_cacerts);
//                            unknownCA = true;
//                        }
//                    } catch (OCSPException e) {
//                        log.error("Unable to generate CA certificate hash.", e);
//                        cacert = null;
//                        continue;
//                    }
//                    // Create a basic response (if we haven't done it already) using the first issuer we find, or the default one
//                    if ((cacert != null) && (basicRes == null)) {
//                        basicRes = createOCSPResponse(req, cacert);
//                        if (log.isDebugEnabled()) {
//                            if (m_useCASigningCert) {
//                                log.debug("Signing OCSP response directly with CA: " + cacert.getSubjectDN().getName());
//                            } else {
//                                log.debug("Signing OCSP response with OCSP signer of CA: " + cacert.getSubjectDN().getName());
//                            }
//                        }
//                    } else if (cacert == null) {
//                        final String msg = "Unable to find CA certificate by issuer name hash: " + Hex.encode(certId.getIssuerNameHash()) + ", or even the default responder: " + m_defaultResponderId;
//                        log.error(msg);
//                        continue;
//                    }
//                    if (unknownCA == true) {
//                        final String msg = "Unable to find CA certificate by issuer name hash: " + Hex.encode(certId.getIssuerNameHash()) + ", using the default reponder to send 'UnknownStatus'";
//                        log.info(msg);
//                        // If we can not find the CA, answer UnknowStatus
//                        basicRes.addResponse(certId, new UnknownStatus());
//                        continue;
//                    }
//
//
//                    /*
//                     * Implement logic according to
//                     * chapter 2.7 in RFC2560
//                     *
//                     * 2.7  CA Key Compromise
//                     *    If an OCSP responder knows that a particular CA's private key has
//                     *    been compromised, it MAY return the revoked state for all
//                     *    certificates issued by that CA.
//                     */
//                    RevokedCertInfo rci;
//                    rci = m_certStore.isRevoked(m_adm
//                            , cacert.getIssuerDN().getName()
//                            , cacert.getSerialNumber());
//                    if (null != rci && rci.getReason() == RevokedCertInfo.NOT_REVOKED) {
//                        rci = null;
//                    }
//                    if (null == rci) {
//                        rci = m_certStore.isRevoked(m_adm
//                                , cacert.getSubjectDN().getName()
//                                , certId.getSerialNumber());
//                        if (null == rci) {
//                            log.debug("Unable to find revocation information for certificate with serial '"
//                                    + certId.getSerialNumber() + "'"
//                                    + " from issuer '" + cacert.getSubjectDN().getName() + "'");
//                            basicRes.addResponse(certId, new UnknownStatus());
//                        } else {
//                            CertificateStatus certStatus = null; // null mean good
//                            if (rci.getReason() != RevokedCertInfo.NOT_REVOKED) {
//                                certStatus = new RevokedStatus(new RevokedInfo(new DERGeneralizedTime(rci.getRevocationDate()),
//                                        new CRLReason(rci.getReason())));
//                            } else {
//                                certStatus = null;
//                            }
//                            if (log.isDebugEnabled()) {
//                                log.debug("Adding status information for certificate with serial '"
//                                        + certId.getSerialNumber() + "'"
//                                        + " from issuer '" + cacert.getSubjectDN().getName() + "'");
//                            }
//                            basicRes.addResponse(certId, certStatus);
//                        }
//                    } else {
//                        CertificateStatus certStatus = new RevokedStatus(new RevokedInfo(new DERGeneralizedTime(rci.getRevocationDate()),
//                                new CRLReason(rci.getReason())));
//                        basicRes.addResponse(certId, certStatus);
//                    }
//                }
//                if ((basicRes != null) && (cacert != null)) {
//                    // generate the signed response object
//                    BasicOCSPResp basicresp = signOCSPResponse(basicRes, cacert);
//                    ocspresp = res.generate(OCSPRespGenerator.SUCCESSFUL, basicresp);
//                } else {
//                    final String msg = "Unable to find CA certificate and key to generate OCSP response!";
//                    log.error(msg);
//                    throw new ServletException(msg);
//                }
//            } catch (MalformedRequestException e) {
//                log.info("MalformedRequestException caught : ", e);
//                // generate the signed response object
//                BasicOCSPResp basicresp = signOCSPResponse(basicRes, cacert);
//                ocspresp = res.generate(OCSPRespGenerator.MALFORMED_REQUEST, basicresp);
//            } catch (SignRequestException e) {
//                log.info("SignRequestException caught : ", e);
//                // generate the signed response object
//                BasicOCSPResp basicresp = signOCSPResponse(basicRes, cacert);
//                ocspresp = res.generate(OCSPRespGenerator.SIG_REQUIRED, basicresp);
//            } catch (Exception e) {
//                log.error("Unable to handle OCSP request.", e);
//                if (e instanceof ServletException)
//                    throw (ServletException) e;
//                // generate the signed response object
//                BasicOCSPResp basicresp = signOCSPResponse(basicRes, cacert);
//                ocspresp = res.generate(OCSPRespGenerator.INTERNAL_ERROR, basicresp);
//            }
//            byte[] respBytes = ocspresp.getEncoded();
//            response.setContentType("application/ocsp-response");
//            //response.setHeader("Content-transfer-encoding", "binary");
//            response.setContentLength(respBytes.length);
//            response.getOutputStream().write(respBytes);
//            response.getOutputStream().flush();
//        } catch (OCSPException e) {
//            log.error("OCSPException caught, fatal error : ", e);
//            throw new ServletException(e);
//        } catch (IllegalExtendedCAServiceRequestException e) {
//            log.error("Can't generate any type of OCSP response: ", e);
//            throw new ServletException(e);
//        } catch (CADoesntExistsException e) {
//            log.error("CA used to sign OCSP response does not exist: ", e);
//            throw new ServletException(e);
//        } catch (ExtendedCAServiceNotActiveException e) {
//            log.error("Error in CAs extended service: ", e);
//            throw new ServletException(e);
//        } catch (ExtendedCAServiceRequestException e) {
//            log.error("Error in CAs extended service: ", e);
//            throw new ServletException(e);
//        }
//        log.debug("<service()");
//    }
} // OCSPServlet
