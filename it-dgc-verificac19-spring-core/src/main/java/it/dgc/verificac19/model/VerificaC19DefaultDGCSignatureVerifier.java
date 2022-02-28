/**
 * 
 */
package it.dgc.verificac19.model;

import java.security.SignatureException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.upokecenter.cbor.CBORException;
import it.dgc.verificac19.exception.VerificaC19CertificateExpiredDGCException;
import it.dgc.verificac19.exception.VerificaC19SignatureDGCException;
import se.digg.dgc.signatures.CertificateProvider;
import se.digg.dgc.signatures.DGCSignatureVerifier;
import se.digg.dgc.signatures.cose.CoseSign1_Object;
import se.digg.dgc.signatures.cwt.Cwt;
import se.digg.dgc.signatures.impl.DefaultDGCSignatureVerifier;

/**
 * @author NIGFRA
 *
 */
public class VerificaC19DefaultDGCSignatureVerifier extends DefaultDGCSignatureVerifier {

  private static final Logger LOG =
      LoggerFactory.getLogger(VerificaC19DefaultDGCSignatureVerifier.class);


  @Override
  public Result verify(byte[] signedCwt, CertificateProvider certificateProvider)
      throws SignatureException, CertificateExpiredException {

    if (certificateProvider == null) {
      throw new IllegalArgumentException("certificateProvider must be supplied");
    }

    try {
      final CoseSign1_Object coseObject = CoseSign1_Object.decode(signedCwt);

      final byte[] kid = coseObject.getKeyIdentifier();

      Cwt cwt = coseObject.getCwt();

      final String country = cwt.getIssuer();
      final byte[] dgcPayload = cwt.getDgcV1();

      if (dgcPayload == null) {
        throw new SignatureException("No DCC payload available in CWT");
      }

      if (kid == null && country == null) {
        throw new VerificaC19SignatureDGCException(dgcPayload,
            "Signed object does not contain key identifier or country - cannot find certificate");
      }

      final List<X509Certificate> certs = certificateProvider.getCertificates(country, kid);
      for (final X509Certificate cert : certs) {
        LOG.trace("Attempting DCC signature verification using certificate '{}'",
            cert.getSubjectX500Principal().getName());

        try {
          coseObject.verifySignature(cert.getPublicKey());
          LOG.debug("DCC signature verification succeeded using certificate '{}'",
              cert.getSubjectX500Principal().getName());

          // ensure that the HCERT hasn't expired.
          final Instant expiration = cwt.getExpiration();
          if (expiration != null) {
            if (Instant.now().isAfter(expiration)) {
              throw new VerificaC19CertificateExpiredDGCException(dgcPayload, "Signed DCC has expired");
            }
          } else {
            LOG.warn("Signed HCERT did not contain an expiration time - assuming it is valid");
          }

          return new DGCSignatureVerifier.Result(dgcPayload, cert, kid, country, cwt.getIssuedAt(),
              cwt.getExpiration());
        } catch (CBORException | SignatureException e) {
          LOG.info("DGC signature verification failed using certificate '{}' - {}",
              cert.getSubjectX500Principal().getName(), e.getMessage(), e);
        }
      }
      if (certs.isEmpty()) {
        throw new VerificaC19SignatureDGCException(dgcPayload, "No signer certificates could be found");
      } else {
        throw new VerificaC19SignatureDGCException(dgcPayload,
            "Signature verification failed for all attempted certificates");
      }

    } catch (final CBORException e) {
      throw new SignatureException("Invalid signature - " + e.getMessage(), e);
    }
  }

}
