package com.phoenix.service.revocation;

import com.phoenix.db.CAcertsSigned;
import com.phoenix.db.CrlHolder;
import com.phoenix.service.PhoenixDataService;
import com.phoenix.service.PhoenixServerCASigner;
import com.phoenix.service.ServerCommandExecutor;
import com.phoenix.service.ServerMICommand;
import com.phoenix.utils.MiscUtils;
import org.bouncycastle.cert.X509CRLHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
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

    @Autowired(required = true)
    private RevocationExecutor executor;

    @Autowired(required = true)
    private ServerCommandExecutor serverCmdExecutor;

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
        long numRecords = 0;
        final List<CAcertsSigned> revoked = loadRevokedCertificates();
        final X509v2CRLBuilder crlGenerator = signer.getCrlGenerator(newSerial);
        for(CAcertsSigned revokedCert : revoked){
            signer.addCRLEntry(crlGenerator, BigInteger.valueOf(revokedCert.getSerial()), revokedCert.getDateRevoked());
            numRecords += 1;
        }

        final X509CRLHolder crlHolder = signer.generateCrl(crlGenerator);
        final X509CRL crlObj = signer.getCRLFromHolder(crlHolder);
        lastCrl.setCrl(crlObj);
        lastCrl.setRawCrl(crlObj.getEncoded());
        lastCrl.setNumberOfRecords(numRecords);

        // Generate PEM representation.
        lastCrl.updatePem();

        dataService.persist(lastCrl);
        onCrlUpdated(lastCrl);
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
            lastCrl.setNumberOfRecords(lastCrl.getNumberOfRecords() + 1);

            // Generate PEM representation.
            lastCrl.updatePem();

            dataService.persist(lastCrl);
            onCrlUpdated(lastCrl);
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
        long numRecords = 0;
        final List<CAcertsSigned> revoked = loadRevokedCertificates();
        final X509v2CRLBuilder crlGenerator = signer.getCrlGenerator(BigInteger.ONE);
        for(CAcertsSigned revokedCert : revoked){
            if (!givenSerialAlreadyRevoked && certificateSerial == revokedCert.getSerial()){
                givenSerialAlreadyRevoked = true;
            }

            signer.addCRLEntry(crlGenerator, BigInteger.valueOf(revokedCert.getSerial()), revokedCert.getDateRevoked());
            numRecords += 1;
        }

        // Add one passed as parameter.
        if (!givenSerialAlreadyRevoked){
            signer.addCRLEntry(crlGenerator, BigInteger.valueOf(certificateSerial), new Date());
            numRecords += 1;
        }

        final X509CRLHolder crlHolder = signer.generateCrl(crlGenerator);
        final X509CRL crlObj = signer.getCRLFromHolder(crlHolder);
        newCrl.setCrl(crlObj);
        newCrl.setRawCrl(crlObj.getEncoded());
        newCrl.setNumberOfRecords(numRecords);

        // Generate PEM representation.
        newCrl.updatePem();

        dataService.persist(newCrl);
        onCrlUpdated(newCrl);
        return newCrl;
    }

    /**
     * Returns CRL in PEM format.
     * openssl crl -in revoked.der -inform PEM -text -noout
     *
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

    /**
     * Returns CRL in DER format.
     *  openssl crl -in revoked.der -inform DER -text -noout
     *
     * @param request
     * @param response
     * @return
     */
    @RequestMapping(value="/ca/revoked.der", method=RequestMethod.GET)
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT, readOnly = true)
    public @ResponseBody byte[] getRevocationListDer(HttpServletRequest request, HttpServletResponse response) {
        final CrlHolder lastCrl = getLastCrl();
        if (lastCrl == null || lastCrl.getRawCrl() == null){
            log.info("Last CRL is null/empty");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return new byte[0];
        }

        response.setStatus(HttpServletResponse.SC_OK);
        return lastCrl.getRawCrl();

//        return ResponseEntity.ok()
//                .contentLength(gridFsFile.getLength())
//                .contentType(MediaType.parseMediaType(gridFsFile.getContentType()))
//                .body(new InputStreamResource(gridFsFile.getInputStream()));
    }

    /**
     * Called when CRL gets updated and this update should be reflected somewhere.
     */
    public void onCrlUpdated(CrlHolder newCrl){
        // TODO: change to loose coupling with Guava event bus.
        executor.propagateCrlAsync(newCrl, false);
    }

    /**
     * Rewrites CRL file, called on CRL change.
     * @param newCrl
     */
    public void propagateCrlRefresh(CrlHolder newCrl){
        log.info("Propagating CRL change.");
        // TODO: regenerate Opensips CRL
        // TODO: refactor, AMQP message to all components about CRL change, so they update it... Opensips watcher listening to AMQP.
        final String crlDir = "/etc/opensips/crl/";
        final String crlFile = "phonex.crl";

        FileOutputStream out = null;
        try {
            final File crlDirF = new File(crlDir);
            if (!crlDirF.exists()){
                crlDirF.mkdirs();
            }

            if (!crlDirF.canWrite()){
                log.error("Cannot write to CRL directory {}", crlDirF.getAbsolutePath());
            }

            final File destCrl = new File(crlDir + crlFile);
            out = new FileOutputStream(destCrl, false);
            out.write(newCrl.getPemCrl().getBytes("UTF-8"));
            out.flush();

            log.info("Going to call refresh cmd");
            final ServerMICommand refreshCmd = new ServerMICommand("refresh_crl_ca");
            serverCmdExecutor.addToQueue(refreshCmd);

        } catch(Exception e){
            log.error("Could not dump CRL file, user: " + System.getProperty("user.name"), e);
        } finally {
            MiscUtils.closeSilently(out);
        }
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
