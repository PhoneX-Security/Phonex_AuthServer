package com.phoenix.service.revocation;

import com.phoenix.db.CAcertsSigned;
import com.phoenix.db.CrlHolder;
import com.phoenix.service.PhoenixDataService;
import com.phoenix.service.PhoenixServerCASigner;
import org.bouncycastle.cert.X509CRLHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509CRL;
import java.util.Date;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Service takes care about certificate revocation.
 *
 * Created by dusanklinec on 23.11.15.
 */
@Service
@Repository
@Controller
public class RevocationManager {
    private static final Logger log = LoggerFactory.getLogger(RevocationManager.class);

    @Autowired
    private PhoenixDataService dataService;

    @Autowired(required = true)
    private PhoenixServerCASigner signer;

    @PostConstruct
    public synchronized void init() {
        log.info("Initializing RevocationManager");
    }

    @PreDestroy
    public synchronized void deinit(){
        log.info("Shutting down RevocationManager");
    }

    /**
     * Returns CRL stored in database.
     * Need to be called from transactional method.
     *
     * @return
     */
    public CrlHolder getLastCrl() {
        final String sql = "SELECT crl FROM CrlHolder crl WHERE crl.domain = :domain";
        final TypedQuery<CrlHolder> query = dataService.createQuery(sql, CrlHolder.class);
        query.setParameter("domain", "phone-x.net");
        query.setMaxResults(1);

        final List<CrlHolder> resultList = query.getResultList();
        if (resultList == null || resultList.isEmpty()){
            return null;
        }

        CrlHolder holder = resultList.get(0);
        if (holder != null){
            holder.tryUpdateCrl();
        }

        return holder;
    }

    /**
     * Loads revoked certificates.
     * Does not load certificate owner / der certificate or certifcate hash, loads only serial and revocation projection.
     * @return
     */
    protected List<CAcertsSigned> loadRevokedCertificates(){
        final String sqlFetch = "SELECT NEW CAcertsSigned(cert.serial, cert.isRevoked, cert.dateRevoked, cert.revokedReason) " +
                " FROM CAcertsSigned cert WHERE cert.isRevoked = 1";

        final TypedQuery<CAcertsSigned> query = dataService.createQuery(sqlFetch, CAcertsSigned.class);
        return query.getResultList();
    }

    /**
     * Generates CRL completely from the certificate records in the database.
     * Loads previously generated CRL, if any, and continues with serial number one bigger than previous.
     * @return new database record with filled in CRL.
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public synchronized CrlHolder generateNewCrl() throws OperatorCreationException, CertificateEncodingException, CertIOException, NoSuchAlgorithmException, CRLException {
        CrlHolder lastCrl = getLastCrl();
        BigInteger newSerial = null;
        boolean newOne = false;

        if (lastCrl == null){
            lastCrl = new CrlHolder();
            lastCrl.setDomain("phone-x.net");
            lastCrl.setCaId(signer.getCAIdentifier());
            newSerial = BigInteger.ONE;
            newOne = true;

        } else {
            newSerial = BigInteger.valueOf(lastCrl.getSerial() + 1);
        }

        lastCrl.setCaId(signer.getCAIdentifier());
        lastCrl.setSerial(newSerial.longValue());
        lastCrl.setTimeGenerated(new Date());

        // Loading all revoked certificates from database, may take some time.
        final List<CAcertsSigned> revoked = loadRevokedCertificates();
        final X509v2CRLBuilder crlGenerator = signer.getCrlGenerator(newSerial);
        for(CAcertsSigned revokedCert : revoked){
            signer.addCRLEntry(crlGenerator, BigInteger.valueOf(revokedCert.getSerial()), revokedCert.getDateRevoked());
        }

        final X509CRLHolder crlHolder = signer.generateCrl(crlGenerator);
        final X509CRL crlObj = signer.getCRLFromHolder(crlHolder);
        lastCrl.setCrl(crlObj);
        lastCrl.setRawCrl(crlObj.getEncoded());

        // Generate PEM representation.
        lastCrl.updatePem();

        dataService.persist(lastCrl);
        return lastCrl;
    }

    /**
     * Adds a new CRL entry to the CRL in the database.
     * @param certificateSerial
     * @return
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = false)
    public synchronized CrlHolder addNewCrlEntry(long certificateSerial) throws IOException, NoSuchAlgorithmException, OperatorCreationException, CertificateEncodingException, CRLException {
        CrlHolder lastCrl = getLastCrl();
        if (lastCrl != null){
            final X509CRL crlObj = lastCrl.getCrl();
            X509CRLHolder crlHolder = new X509CRLHolder(lastCrl.getRawCrl());

            // New CRL builder, increase serial by one.
            final X509v2CRLBuilder crlGenerator = signer.getCrlGenerator(BigInteger.valueOf(lastCrl.getSerial() + 1));

            // Add all existing CRL entries from existing CRL.
            crlGenerator.addCRL(crlHolder);

            // Add new entry to the CRL.
            signer.addCRLEntry(crlGenerator, BigInteger.valueOf(certificateSerial), new Date());

            // Generate new CRL objects.
            final X509CRLHolder newCrlHolder = signer.generateCrl(crlGenerator);
            final X509CRL newCrlObj = signer.getCRLFromHolder(newCrlHolder);
            lastCrl.setCrl(crlObj);
            lastCrl.setRawCrl(crlObj.getEncoded());

            // Generate PEM representation.
            lastCrl.updatePem();

            dataService.persist(lastCrl);
            return lastCrl;
        }

        // LastCrl is empty -> new one has to be created.
        boolean givenSerialAlreadyRevoked = false;
        final CrlHolder newCrl = new CrlHolder();
        newCrl.setDomain("phone-x.net");
        newCrl.setCaId(signer.getCAIdentifier());
        newCrl.setSerial(1L);
        newCrl.setTimeGenerated(new Date());

        // Loading all revoked certificates from database, may take some time.
        final List<CAcertsSigned> revoked = loadRevokedCertificates();
        final X509v2CRLBuilder crlGenerator = signer.getCrlGenerator(BigInteger.ONE);
        for(CAcertsSigned revokedCert : revoked){
            if (!givenSerialAlreadyRevoked && certificateSerial == revokedCert.getSerial()){
                givenSerialAlreadyRevoked = true;
            }

            signer.addCRLEntry(crlGenerator, BigInteger.valueOf(revokedCert.getSerial()), revokedCert.getDateRevoked());
        }

        // Add one passed as parameter.
        if (!givenSerialAlreadyRevoked){
            signer.addCRLEntry(crlGenerator, BigInteger.valueOf(certificateSerial), new Date());
        }

        final X509CRLHolder crlHolder = signer.generateCrl(crlGenerator);
        final X509CRL crlObj = signer.getCRLFromHolder(crlHolder);
        newCrl.setCrl(crlObj);
        newCrl.setRawCrl(crlObj.getEncoded());

        // Generate PEM representation.
        newCrl.updatePem();

        dataService.persist(newCrl);
        return newCrl;
    }

    /**
     * Simple demonstration URL to test JSON output converter and dependency injection.
     * @param request
     * @param response
     * @return
     */
    @RequestMapping(value="/ca/revoked.crl", method=RequestMethod.GET)
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    public @ResponseBody String getRevocationList(HttpServletRequest request, HttpServletResponse response) {
        final CrlHolder lastCrl = getLastCrl();
        if (lastCrl == null || lastCrl.getPemCrl() == null){
            log.info("Last CRL is null/empty");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "";
        }

        response.setStatus(HttpServletResponse.SC_OK);
        return lastCrl.getPemCrl();
    }

    public PhoenixDataService getDataService() {
        return dataService;
    }

    public void setDataService(PhoenixDataService dataService) {
        this.dataService = dataService;
    }

    public PhoenixServerCASigner getSigner() {
        return signer;
    }

    public void setSigner(PhoenixServerCASigner signer) {
        this.signer = signer;
    }
}
