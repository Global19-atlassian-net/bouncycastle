package org.bouncycastle.openpgp.operator.jcajce;

import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.jcajce.io.OutputStreamFactory;
import org.bouncycastle.jcajce.util.DefaultJcaJceHelper;
import org.bouncycastle.jcajce.util.NamedJcaJceHelper;
import org.bouncycastle.jcajce.util.ProviderJcaJceHelper;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPRuntimeOperationException;
import org.bouncycastle.openpgp.operator.PGPContentVerifier;
import org.bouncycastle.openpgp.operator.PGPContentVerifierBuilder;
import org.bouncycastle.openpgp.operator.PGPContentVerifierBuilderProvider;

import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;

public class JcaPGPContentVerifierBuilderProvider
    implements PGPContentVerifierBuilderProvider
{
    private OperatorHelper helper = new OperatorHelper(new DefaultJcaJceHelper());
    private JcaPGPKeyConverter keyConverter = new JcaPGPKeyConverter();

    public JcaPGPContentVerifierBuilderProvider()
    {
    }

    public JcaPGPContentVerifierBuilderProvider setProvider(Provider provider)
    {
        this.helper = new OperatorHelper(new ProviderJcaJceHelper(provider));
        keyConverter.setProvider(provider);

        return this;
    }

    public JcaPGPContentVerifierBuilderProvider setProvider(String providerName)
    {
        this.helper = new OperatorHelper(new NamedJcaJceHelper(providerName));
        keyConverter.setProvider(providerName);

        return this;
    }

    public PGPContentVerifierBuilder get(int keyAlgorithm, int hashAlgorithm)
        throws PGPException
    {
        return new JcaPGPContentVerifierBuilder(keyAlgorithm, hashAlgorithm);
    }

    private class JcaPGPContentVerifierBuilder
        implements PGPContentVerifierBuilder
    {
        private int hashAlgorithm;
        private int keyAlgorithm;

        public JcaPGPContentVerifierBuilder(int keyAlgorithm, int hashAlgorithm)
        {
            this.keyAlgorithm = keyAlgorithm;
            this.hashAlgorithm = hashAlgorithm;
        }

        public PGPContentVerifier build(final PGPPublicKey publicKey)
            throws PGPException
        {
            final Signature signature = helper.createSignature(keyAlgorithm, hashAlgorithm);
            final PublicKey jcaKey = keyConverter.getPublicKey(publicKey);

            try
            {
                signature.initVerify(jcaKey);
            }
            catch (InvalidKeyException e)
            {
                throw new PGPException("invalid key.", e);
            }

            final MessageDigest prehash;
            if (keyAlgorithm == PublicKeyAlgorithmTags.EDDSA)
            {
                try {
                    prehash = helper.createDigest(hashAlgorithm);
                } catch (GeneralSecurityException e) {
                    throw new PGPException("Failed to create prehash digest", e);
                }
            }
            else
            {
                prehash = null;
            }

            return new PGPContentVerifier()
            {
                public int getHashAlgorithm()
                {
                    return hashAlgorithm;
                }

                public int getKeyAlgorithm()
                {
                    return keyAlgorithm;
                }

                public long getKeyID()
                {
                    return publicKey.getKeyID();
                }

                public boolean verify(byte[] expected)
                {
                    try
                    {
                        if (prehash != null)
                        {
                            signature.update(prehash.digest());
                        }

                        // an RSA PGP signature is stored as an MPI, this can occasionally result in a short
                        // signature if there is a leading zero.
                        if (jcaKey instanceof RSAPublicKey)
                        {
                            int modLength = (((RSAPublicKey)jcaKey).getModulus().bitLength() + 7) / 8;
                            if (expected.length < modLength)
                            {
                                byte[] tmp = new byte[modLength];

                                System.arraycopy(expected, 0, tmp, tmp.length - expected.length, expected.length);
           
                                return signature.verify(tmp);
                            }
                        }
                        return signature.verify(expected);
                    }
                    catch (SignatureException e)
                    {
                        throw new PGPRuntimeOperationException("unable to verify signature: " + e.getMessage(), e);
                    }
                }

                public OutputStream getOutputStream()
                {
                    if (prehash != null)
                    {
                        return OutputStreamFactory.createStream(prehash);
                    }
                    else
                    {
                        return OutputStreamFactory.createStream(signature);
                    }
                }
            };
        }
    }
}
