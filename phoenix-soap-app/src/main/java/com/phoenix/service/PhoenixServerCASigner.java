/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import com.phoenix.db.CAcertsSigned;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.CRLException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.Date;
import java.util.Formatter;
import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;

import org.bouncycastle.asn1.DEREnumerated;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.*;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CRLHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.x509.extension.X509ExtensionUtil;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Signature generator for Phoenix from Server CA
 * @author ph4r05
 */
@Service
public class PhoenixServerCASigner {
    private static final Logger log = LoggerFactory.getLogger(PhoenixServerCASigner.class);
    
    // new provider name
    private static final String BC = org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME;
    
    @Autowired
    private SessionFactory sessionFactory;
    
    @PersistenceContext
    protected EntityManager em;
    
    @Autowired(required=true)
    private TrustVerifier trustManager;
    
    //private static final String keystoreResource = "serverCA.jks";
    private static final String keystoreResource = "signing-ca-1.p12";
    private static final String keystorePass = "Eep5aih3yeiH";
    private static final String keystoreAlias = "signing-ca-1";
    private RSAPrivateCrtKeyParameters caPrivateKey;
    private X509Certificate caCert;
    private RSAPrivateCrtKey privKey;
    private volatile boolean caInitialized = false;

    /**
     * Initializes internal running thread.
     */
    @PostConstruct
    public synchronized void init() {
        log.info("Initializing CA module");
        initCA();
    }

    /**
     * Initializes Server CA keystores
     */
    public synchronized void initCA(){
        if (caInitialized){
            return;
        }
        
        /**
         * Add bouncy castle provider
         */
        try {
            Security.addProvider(new BouncyCastleProvider());
            log.info("!! Bouncy castle provider added");
        } catch (Exception ex){
            log.error("Problem during BouncyCastle provider add", ex);
        }
        
        /**
         * At first obtain byte stream from resources - serverCA
         */ 
        InputStream inStream = null;
        try {
            // get input stream for CA key store
            inStream = TrustVerifier.class.getClassLoader().getResourceAsStream(keystoreResource);

            // open keystore and load data
            KeyStore caKs = KeyStore.getInstance("PKCS12");
            caKs.load(inStream, keystorePass.toCharArray());
            
            // load the key entry from the keystore
            Key key = caKs.getKey(keystoreAlias, keystorePass.toCharArray());
            if (key == null) {
                    throw new RuntimeException("Got null key from keystore!"); 
            }
            
            privKey = (RSAPrivateCrtKey) key;
            caPrivateKey = new RSAPrivateCrtKeyParameters(
                    privKey.getModulus(), 
                    privKey.getPublicExponent(), 
                    privKey.getPrivateExponent(),
                    privKey.getPrimeP(), 
                    privKey.getPrimeQ(), 
                    privKey.getPrimeExponentP(), 
                    privKey.getPrimeExponentQ(), 
                    privKey.getCrtCoefficient());
            
            // and get the certificate
            caCert = (X509Certificate) caKs.getCertificate(keystoreAlias);
            
            if (caCert == null) {
                    throw new RuntimeException("Got null cert from keystore!"); 
            }
            
            log.info("Successfully loaded CA key and certificate. CA DN is '" + caCert.getSubjectDN().getName() + "'");
            
            // 
            // Code below is disabled - no need to be paranoid and verify certificates
            //
            //caCert.verify(this.trustManager.getPrimaryCA().getPublicKey());
            //log.info("Successfully verified CA certificate with Primary CA.");
            
            //
            // Code below is disabled - verification with own public key.
            // This is not self signed! It was signed by root CA
            //
            //caCert.verify(caCert.getPublicKey());
            //log.info("Successfully verified CA certificate with its own public key.");
            
            // tidy up
            inStream.close();
        } catch (Exception ex) {
            log.warn("Exception during CA initialization", ex);
        } finally {
            try {
                if(inStream!=null){
                    inStream.close();
                }
            } catch (IOException ex) {
                log.warn("Cannot close input stream", ex);
            }
        }
        
        caInitialized = true;
    }
    
    /**
     * Instantiate CSR from byte representation. May be in DER or PEM format.
     * @param csr
     * @return 
     */
    public PKCS10CertificationRequest getReuest(byte[] csr, boolean isDer) throws IOException{
        if (isDer==false){
            // certificatin request is in PEM -> PEM reader to read it. Iterpret it as
            // base64 encoded string, encoding should not matter here
            PEMReader pemReader = new PEMReader(new InputStreamReader(new ByteArrayInputStream(csr)));
            
            // PEM reader returns still old certification request - getEncoded() and constructor
            org.bouncycastle.jce.PKCS10CertificationRequest tmpObj =      
                 (org.bouncycastle.jce.PKCS10CertificationRequest) pemReader.readObject();
            
            return new PKCS10CertificationRequest(tmpObj.getEncoded());
        } else {
            return new PKCS10CertificationRequest(csr);
        }
    }
    
    /**
     * Returns PEM format of Certificate
     *
     * @return
     * @throws IOException
     * @throws CertificateEncodingException
     */
    public static byte[] getCertificateAsPEM(X509Certificate cert) throws IOException, CertificateEncodingException {
        final String type = "CERTIFICATE";
        byte[] encoding = cert.getEncoded();
        PemObject pemObject = new PemObject(type, encoding);
        return createPEM(pemObject);
    }

    /**
     * Returns PEM format of CRL
     *
     * @return
     * @throws IOException
     * @throws CRLException
     */
    public static byte[] getCRLAsPEM(X509CRL cert) throws IOException, CRLException {
        final String type = "X509 CRL";
        byte[] encoding = cert.getEncoded();
        PemObject pemObject = new PemObject(type, encoding);
        return createPEM(pemObject);
    }

    /**
     * Creates PEM object representation and returns byte array
     *
     * @param obj
     * @return
     * @throws IOException
     */
    public static byte[] createPEM(Object obj) throws IOException {
        ByteArrayOutputStream barrout = new ByteArrayOutputStream();
        createPEM(new OutputStreamWriter(barrout), obj);
        // return encoded PEM data - collect bytes from ByteArrayOutputStream		
        return barrout.toByteArray();
    }
   
    /**
     * Creates PEM file from passed object
     *
     * @param writer
     * @throws IOException
     */
    public static void createPEM(Writer writer, Object obj) throws IOException {
        PEMWriter pemWrt = new PEMWriter(writer, BC);
        pemWrt.writeObject(obj);
        pemWrt.flush();
        pemWrt.close();
    }
    
    /**
     * Returns simple SHA512 certificate digest
     * @param cert
     * @return 
     */
    public String getCertificateDigest(X509Certificate cert) throws NoSuchAlgorithmException, IOException, CertificateEncodingException{
         MessageDigest sha = MessageDigest.getInstance("SHA-512");
         //byte[] digest = ByteStreams.getDigest(ByteStreams.newInputStreamSupplier(cert.getEncoded()), sha);
         byte[] digest = sha.digest(cert.getEncoded());
         
         Formatter formatter = new Formatter();
         for (byte b : digest) {
            formatter.format("%02x", b);
         }

        return formatter.toString();
    }
    
    /**
     * Checks revocation against local database
     * @param cert
     * @return 
     */
    public Boolean isCertificateRevoked(X509Certificate cert) throws NoSuchAlgorithmException, IOException, CertificateEncodingException{
        if (cert==null){
            throw new NullPointerException("Null certificate passed");
        }
        
        try {
            //String digest = this.getCertificateDigest(cert);
            String query = "SELECT ca FROM CAcertsSigned ca"
                    + " WHERE ca.serial=:s AND ca.isRevoked=true";
            CAcertsSigned r = em.createQuery(query, CAcertsSigned.class)
                    .setParameter("s", cert.getSerialNumber().longValue())
                    .setMaxResults(1)
                    .getSingleResult();
            log.info("Certificate returned, should be revoked: " + r.toString());
            return true;
        } catch(NoResultException e){
            // not a revoked certificate
            return false;
        } catch(Exception ex){
            log.info("certificateRevocationCheckException: ", ex);
            return null;
        }
    }
    
    /**
     * Takes a DN and reverses it completely so the first attribute ends up last. 
     * C=SE,O=Foo,CN=Bar becomes CN=Bar,O=Foo,C=SE.
     *
     * @param dn String containing DN to be reversed, The DN string has the format "C=SE, O=xx, OU=yy, CN=zz".
     *
     * @return String containing reversed DN
     */
    public static String reverseDN(String dn) {
        log.debug(">reverseDN: dn: " + dn);
        String ret = null;
        if (dn != null) {
            String o;
            BasicX509NameTokenizer xt = new BasicX509NameTokenizer(dn);
            StringBuffer buf = new StringBuffer();
            boolean first = true;
            while (xt.hasMoreTokens()) {
                o = xt.nextToken();
                //log.debug("token: "+o);
                if (!first) {
                	buf.insert(0,",");
                } else {
                    first = false;                	
                }
                buf.insert(0,o);
            }
            if (buf.length() > 0) {
            	ret = buf.toString();
            }
        }
        
        log.debug("<reverseDN: resulting DN=" + ret);
        return ret;
    } //reverseDN
    
    /**
     * Sign certificate with server CA private key.
     * X509v3 extensions are added - CA:False, subjectKeyIdentifier, authorityKeyIdentifier.
     * 
     * @param inputCSR
     * @return
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws SignatureException
     * @throws IOException
     * @throws OperatorCreationException
     * @throws CertificateException 
     */
    public X509Certificate sign(PKCS10CertificationRequest inputCSR, BigInteger serial, Date notBefore, Date notAfter)
            throws InvalidKeyException, NoSuchAlgorithmException,
            NoSuchProviderException, SignatureException, IOException,
            OperatorCreationException, CertificateException {

        // get assymetric key material from server CA
        AsymmetricKeyParameter pkey  = PrivateKeyFactory.createKey(privKey.getEncoded());
        SubjectPublicKeyInfo keyInfo = SubjectPublicKeyInfo.getInstance(caCert.getPublicKey().getEncoded());

        // construct holder from request
        // OLD 1.46 version, now holder is removed, request is deprecated in original package
        //PKCS10CertificationRequestHolder pk10Holder = new PKCS10CertificationRequestHolder(inputCSR);
        
        // certificate builder - issuer = from ca, subject = from CSR, not before
        // not after = trivial, serial = from database.
        //
        // Create true Subject DN of CA Cert in order to avoid reversed DN - reversed DN fails 
        // certificate verification. 
        // http://stackoverflow.com/questions/7567837/attributes-reversed-in-certificate-subject-and-issuer
        String realSubjectDN = reverseDN(caCert.getSubjectX500Principal().getName());
        log.info("Reversed order: " + realSubjectDN);
        
        X509v3CertificateBuilder certGen = new X509v3CertificateBuilder(
                new X500Name(realSubjectDN),                                  // issuer DN
                serial,                                                       // serial
                notBefore,                                                    // not before
                notAfter,                                                     // not after
                inputCSR.getSubject(),
                inputCSR.getSubjectPublicKeyInfo());
        
        // X509v3 extensions:
        // 1. ensure it has CA:FALSE
        certGen.addExtension(X509Extension.basicConstraints, true, new BasicConstraints(false));
        // 2. key identifier
        JcaX509ExtensionUtils ut = new JcaX509ExtensionUtils();
        SubjectKeyIdentifier ski = ut.createSubjectKeyIdentifier(inputCSR.getSubjectPublicKeyInfo());
        certGen.addExtension(X509Extension.subjectKeyIdentifier, false, ski);
        // 3. auth identifier
        AuthorityKeyIdentifier aui = ut.createAuthorityKeyIdentifier(keyInfo);
        certGen.addExtension(X509Extension.authorityKeyIdentifier, false, aui);
        
        // building new content signer object - will sign with server CA private key (pkey)
        // get signing algorithm
        AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA1withRSA");
        AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
        ContentSigner sigGen = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(pkey);

        // creating certificate from signature
        X509CertificateHolder holder = certGen.build(sigGen);
        Certificate eeX509CertificateStructure = holder.toASN1Structure();
        
        // old 1.46 version
        //X509CertificateStructure eeX509CertificateStructure = holder.toASN1Structure();

        // certificate factory will generate final certificate representation
        // "BC" shows - Not found X.509 
        //CertificateFactory cf = CertificateFactory.getInstance("X.509", "BC");
        // Thus give directly provided
        CertificateFactory cf = CertificateFactory.getInstance("X.509", new BouncyCastleProvider());
        // or use native java crypto - works also
        //CertificateFactory cf = CertificateFactory.getInstance("X.509");

        // Read Certificate as byte stream from ASN1 structure, creating representable
        // certificate object.
        InputStream is1 = new ByteArrayInputStream(eeX509CertificateStructure.getEncoded());
        X509Certificate theCert = (X509Certificate) cf.generateCertificate(is1);
        is1.close();
        return theCert;
    }

    /**
     * From BouncyCastle CRL Holder makes java.security.X509CRL.
     * 
     * @param crlHolder
     * @return
     * @throws CRLException 
     */
    public X509CRL getCRLFromHolder(X509CRLHolder crlHolder) throws CRLException{
         return new JcaX509CRLConverter().setProvider(BC).getCRL(crlHolder);
    }

    /**
     * Prepares CRL generator with CA public key and given extensions.
     *
     * @param serial
     * @return
     * @throws CertIOException
     * @throws NoSuchAlgorithmException
     * @throws OperatorCreationException
     * @throws CertificateEncodingException
     */
    public X509v2CRLBuilder getCrlGenerator(BigInteger serial) throws CertIOException, NoSuchAlgorithmException, OperatorCreationException, CertificateEncodingException {
        Date now = new Date();
        X509v2CRLBuilder crlGen = new X509v2CRLBuilder(new X500Name(caCert.getSubjectDN().getName()), now);

        Date nextUpdate = new Date(now.getTime() + 7L * 24L * 60L * 60L * 1000L); // Every 7 days
        crlGen.setNextUpdate(nextUpdate);
        crlGen.addExtension(X509Extension.cRLNumber, false, new CRLNumber(serial));//Because we create it. The CRLNumber is 1
        crlGen.addExtension(X509Extension.authorityKeyIdentifier, false, new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(caCert));

        GeneralName gn = new GeneralName(6, new DERIA5String("https://phone-x.net:8442/phoenix/rest/ca/revoked.crl"));
        GeneralNames gns = new GeneralNames(gn);
        DistributionPointName dpn = new DistributionPointName(gns);
        DistributionPoint distp = new DistributionPoint(dpn, null, null);
        crlGen.addExtension(X509Extension.cRLDistributionPoints, false, new DERSequence(distp));

        return crlGen;
    }

    /**
     * Generates CRLHolder from CRL builder.
     * @param crlGen
     * @return
     * @throws OperatorCreationException
     */
    public X509CRLHolder generateCrl(X509v2CRLBuilder crlGen) throws OperatorCreationException {
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(privKey);//sign with our privatekey
        X509CRLHolder crlholder = crlGen.build(contentSigner);

        return crlholder;
    }

    /**
     * Generates new empty CRL.
     *
     * @return
     * @throws CertificateParsingException
     * @throws NoSuchProviderException
     * @throws NoSuchProviderException
     * @throws SecurityException
     * @throws SignatureException
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     * @throws CertIOException
     * @throws OperatorCreationException 
     */
    public X509CRLHolder generateNewCRL() throws CertificateParsingException, NoSuchProviderException, NoSuchProviderException, SecurityException, SignatureException, InvalidKeyException, NoSuchAlgorithmException, CertIOException, OperatorCreationException, CertificateEncodingException {
        final X509v2CRLBuilder crlGen = getCrlGenerator(BigInteger.ONE);
        return generateCrl(crlGen);
    }

    /**
     * Adds crl entry to the CRL builder.
     *
     * @param crlGen
     * @param serialToRevoke
     * @return
     * @throws CRLException
     * @throws OperatorCreationException
     * @throws NoSuchAlgorithmException
     * @throws CertIOException
     * @throws CertificateEncodingException
     */
    public X509v2CRLBuilder addCRLEntry(X509v2CRLBuilder crlGen, BigInteger serialToRevoke, Date when) throws CRLException, OperatorCreationException, NoSuchAlgorithmException, CertIOException, CertificateEncodingException {
        crlGen.addCRLEntry(serialToRevoke, when == null ? new Date() : when, CRLReason.privilegeWithdrawn);
        return crlGen;
    }

    /**
     * Adds new entry to certificate revocation list.
     * @param crl
     * @return
     * @throws CRLException
     * @throws OperatorCreationException
     * @throws NoSuchAlgorithmException
     * @throws CertIOException 
     */
    public X509CRLHolder addCRLEntry(X509CRLHolder crl, BigInteger crlSerial) throws CRLException, OperatorCreationException, NoSuchAlgorithmException, CertIOException, CertificateEncodingException {
        final Date now = new Date();
        final Date nextUpdate = new Date(System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L);
        final X509v2CRLBuilder crlGen = getCrlGenerator(crlSerial);

        crlGen.setNextUpdate(nextUpdate);
        crlGen.addCRL(crl);
        crlGen.addCRLEntry(BigInteger.valueOf(2), now, CRLReason.privilegeWithdrawn);

        X509CRLHolder crlHolder = crlGen.build(new JcaContentSignerBuilder("SHA256withRSAEncryption").setProvider(BC).build(this.privKey));
        return crlHolder;
    }

    /**
     * Check the CRL signature in accordance with the given certificate
     *
     * @param crl
     * @param caCert
     * @return
     */
    public static boolean isCRLValid(X509CRLHolder crl, X509Certificate caCert) {
        try {
            return crl.isSignatureValid(new JcaContentVerifierProviderBuilder().setProvider("BC").build(caCert));

        } catch (Exception e) {
            log.error("CRL validity check failed", e);
            return false;
        }
    }

    /**
     * Return true if the serial is not in the crl, false otherwise
     *
     * @param crl
     * @param serial
     * @return
     */
    public static boolean serialNotInCRL(X509CRLHolder crl, BigInteger serial) {
        X509CRLEntryHolder entry = crl.getRevokedCertificate(serial);
        if (entry == null) {
            return true;
        }
        else {
            log.debug("Certificate number: " + entry.getSerialNumber());
            log.debug("Issuer            : " + crl.getIssuer());
            if (entry.hasExtensions()) {
                Extension ext = entry.getExtension(X509Extension.reasonCode);
                if (ext != null) {
                    DEREnumerated reasonCode;
                    try {
                        reasonCode = (DEREnumerated) X509ExtensionUtil.fromExtensionValue(ext.getExtnValue().getEncoded());
                        log.info("Reason Code      : "+reasonCode.getValue());

                    } catch (IOException e) {
                        log.error("Exception in CRL parsing", e);
                    }
                }
            }
            return false;
        }
    }
    
    /**
     * class for breaking up an X500 Name into it's component tokens, ala
     * java.util.StringTokenizer. Taken from BouncyCastle, but does NOT
     * use or consider escaped characters. Used for reversing DNs without unescaping.
     */
    private static class BasicX509NameTokenizer
    {
        private String          oid;
        private int             index;
        private StringBuffer    buf = new StringBuffer();

        public BasicX509NameTokenizer(
            String oid)
        {
            this.oid = oid;
            this.index = -1;
        }

        public boolean hasMoreTokens()
        {
            return (index != oid.length());
        }

        public String nextToken()
        {
            if (index == oid.length())
            {
                return null;
            }

            int     end = index + 1;
            boolean quoted = false;
            boolean escaped = false;

            buf.setLength(0);

            while (end != oid.length())
            {
                char    c = oid.charAt(end);
                
                if (c == '"')
                {
                    if (!escaped)
                    {
                        buf.append(c);
                        quoted = !quoted;
                    }
                    else
                    {
                        buf.append(c);
                    }
                    escaped = false;
                }
                else
                { 
                    if (escaped || quoted)
                    {
                        buf.append(c);
                        escaped = false;
                    }
                    else if (c == '\\')
                    {
                        buf.append(c);
                        escaped = true;
                    }
                    else if ( (c == ',') && (!escaped) )
                    {
                        break;
                    }
                    else
                    {
                        buf.append(c);
                    }
                }
                end++;
            }

            index = end;
            return buf.toString().trim();
        }
    }
}

