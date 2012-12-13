/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.phoenix.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.Date;
import java.util.Formatter;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
//import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
//import org.bouncycastle.pkcs.PKCS10CertificationRequestHolder;
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
    
    @Autowired
    private SessionFactory sessionFactory;
    
    @PersistenceContext
    protected EntityManager em;
    
    @Autowired(required=true)
    private TrustVerifier trustManager;
    
    //private static final String keystoreResource = "serverCA.jks";
    private static final String keystoreResource = "signing-ca-1.p12";
    private static final String keystorePass = "marklar";
    private static final String keystoreAlias = "signing-ca-1";
    private RSAPrivateCrtKeyParameters caPrivateKey;
    private X509Certificate caCert;
    private RSAPrivateCrtKey privKey;
    
    /**
     * Initializes Server CA keystores
     */
    public void initCA(){
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
            caCert.verify(this.trustManager.getPrimaryCA().getPublicKey());
            log.info("Successfully verified CA certificate with Primary CA.");
            
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
        X509v3CertificateBuilder certGen = new X509v3CertificateBuilder(
                new X500Name(caCert.getIssuerX500Principal().getName()),      // issuer DN
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
}
